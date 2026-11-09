import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { ChatMessage, ProjectChat } from "../../shared/projects";
import type { ChatCore } from "./core";

vi.mock("../agents/accounts", () => ({ accountHomes: () => [] }));

vi.mock(import("../agents/claude/project"), async (actual) => ({
  ...(await actual()),
  // Relay runs mudtoy-d4's session for the exhaust thread; nobody's for others.
  claudeSessionKey: (id: string) =>
    id === "s-exhaust"
      ? JSON.stringify(["data", "exhaust", "main"])
      : undefined,
}));

const { PeerSends } = await import("./peer-sends");

let home: string;

beforeEach(async () => {
  home = await mkdtemp(join(tmpdir(), "relay-peer-sends-"));
  await mkdir(join(home, "sessions"));
  const list = (pid: number, sessionId: string, name: string) =>
    writeFile(
      join(home, "sessions", `${pid}.json`),
      JSON.stringify({ pid, sessionId, name, updatedAt: 1 }),
    );
  await list(1, "s-intro", "mudtoy-11");
  await list(2, "s-exhaust", "mudtoy-d4");
  await list(3, "s-terminal", "relay-50");
});

afterEach(() => rm(home, { recursive: true, force: true }));

const thread = (id: string, title: string, messages: ChatMessage[] = []) =>
  ({
    id,
    title,
    messages,
    sessions: { claude: { thread: `s-${id}` } },
  }) as unknown as ProjectChat;

function setup(threads: ProjectChat[]) {
  const emit = vi.fn();
  const save = vi.fn(async () => {});
  const core = {
    storage: {
      load: async (id: string) => {
        const found = threads.find((t) => t.id === id);
        if (!found) throw new Error("gone");
        return found;
      },
      save,
    },
    emit,
  } as unknown as ChatCore;
  return { peers: new PeerSends(core, () => [home]), emit, save };
}

it("keeps a message to another of Relay's threads on both, each naming the other", async () => {
  const intro = thread("intro", "Studio intro");
  const exhaust = thread("exhaust", "Exhaust smoke");
  const { peers, save } = setup([intro, exhaust]);
  await peers.sent(intro, "claude", undefined, {
    to: "mudtoy-d4",
    message: "Can I have Play after your rebuild?",
  });
  expect(intro.peerMessages).toMatchObject([
    {
      direction: "out",
      peer: { name: "mudtoy-d4", threadId: "exhaust" },
      text: "Can I have Play after your rebuild?",
    },
  ]);
  expect(exhaust.peerMessages).toMatchObject([
    {
      direction: "in",
      peer: { name: "mudtoy-11", threadId: "intro" },
      text: "Can I have Play after your rebuild?",
    },
  ]);
  expect(save).toHaveBeenCalledTimes(2);
});

it("keeps one to a session Relay doesn't run on the sender alone, and nothing for a subagent", async () => {
  const intro = thread("intro", "Studio intro");
  const { peers, save } = setup([intro]);
  await peers.sent(intro, "claude", undefined, {
    to: "relay-50",
    message: "hi",
  });
  expect(intro.peerMessages).toMatchObject([
    { direction: "out", peer: { name: "relay-50" } },
  ]);
  expect(intro.peerMessages![0]!.peer.threadId).toBeUndefined();
  await peers.sent(intro, "claude", undefined, { to: "main", message: "done" });
  expect(intro.peerMessages).toHaveLength(1);
  expect(save).toHaveBeenCalledTimes(1);
});

it("credits the message with the turn it woke, when that turn started first", async () => {
  const intro = thread("intro", "Studio intro");
  const woke: ChatMessage = {
    id: "turn",
    role: "assistant",
    body: "",
    status: "streaming",
    unprompted: true,
    created: Date.now(),
    provider: "claude",
    version: 1,
  };
  const exhaust = thread("exhaust", "Exhaust smoke", [woke]);
  const { peers, emit } = setup([intro, exhaust]);
  await peers.sent(intro, "claude", undefined, {
    to: "mudtoy-d4",
    message: "Play's yours",
  });
  expect(woke.wokenBy).toEqual({ name: "mudtoy-11", threadId: "intro" });
  expect(emit).toHaveBeenCalledWith({
    chatId: "exhaust",
    message: expect.objectContaining({ id: "turn", wokenBy: woke.wokenBy }),
  });
});
