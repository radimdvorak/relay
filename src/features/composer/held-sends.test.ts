import { beforeEach, expect, it, vi } from "vitest";
import type { HeldSend, HeldSendResult } from "../../../shared/drafts";
import type { ProjectChatSend } from "../../../shared/projects";

const api = vi.hoisted(() => ({
  onDraftSend: vi.fn(),
  waitingDraftSends: vi.fn(async () => []),
  finishDraftSend: vi.fn(),
  projects: vi.fn(async () => [{ id: "p1", path: "/p1" }]),
  createProjectChat: vi.fn(),
  sendProjectChat: vi.fn(),
  // Every thread the page made is still there.
  projectChats: vi.fn(() =>
    Promise.all(api.createProjectChat.mock.results.map((made) => made.value)),
  ),
}));

vi.mock("../../lib/api", () => ({ api }));

vi.mock("../images/draft-images", () => ({
  loadDraftImages: async () => [],
  saveDraftImages: async () => {},
  draftImageCount: () => 0,
  draftsWithImages: () => [],
  readDraftImageCounts: async () => {},
  onDraftImageCount: () => () => {},
}));

const audio = vi.hoisted(() => new Map<string, unknown[]>());

vi.mock("../audio/draft-audio", () => ({
  loadDraftAudio: async (key: string) => audio.get(key) ?? [],
  saveDraftAudio: async (key: string, list: unknown[]) =>
    void audio.set(key, list),
  audioSendItems: (
    clips: {
      name: string;
      mimeType: string;
      durationSec?: number;
      peaks?: number[];
      dataUrl: string;
    }[],
  ) =>
    clips.map(({ name, mimeType, durationSec, peaks, dataUrl }) => ({
      name,
      mimeType,
      ...(durationSec !== undefined ? { durationSec } : {}),
      ...(peaks?.length ? { peaks } : {}),
      dataUrl,
    })),
  draftAudioCount: (key: string) => audio.get(key)?.length ?? 0,
  draftsWithAudio: () => [...audio.keys()],
  readDraftAudioCounts: async () => {},
  onDraftAudioCount: () => () => {},
  onDraftAudioSaved: () => () => {},
}));

import { followHeldSends } from "./held-sends";

const store = new Map<string, string>();

beforeEach(() => {
  store.clear();
  audio.clear();
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => store.set(key, value),
    removeItem: (key: string) => store.delete(key),
    key: (i: number) => [...store.keys()][i] ?? null,
    get length() {
      return store.size;
    },
  });
  for (const fn of Object.values(api)) fn.mockClear();
});

const send = (id: string): ProjectChatSend =>
  ({ id, body: "hello", provider: "claude" }) as ProjectChatSend;

/** Follows held sends whose first send fails and second goes; what each answered. */
function failingOnce() {
  let take!: (request: HeldSend) => void;
  api.onDraftSend.mockImplementation((listener) => {
    take = listener;
    return () => {};
  });
  let made = 0;
  api.createProjectChat.mockImplementation(async () => ({
    id: `chat-${++made}`,
  }));
  api.sendProjectChat
    .mockRejectedValueOnce(new Error("Claude isn't signed in"))
    .mockResolvedValueOnce(undefined);
  const results: HeldSendResult[] = [];
  api.finishDraftSend.mockImplementation((_id, result: HeldSendResult) =>
    results.push(result),
  );
  followHeldSends();
  return { take, results };
}

it("sends a new thread's retry to the thread its failed first try made", async () => {
  const { take, results } = failingOnce();
  take({ id: "r1", key: "new:p1", send: send("m1") });
  await vi.waitFor(() => expect(results).toHaveLength(1));
  // Phones retry a message under its own id.
  take({ id: "r2", key: "new:p1", send: send("m1") });
  await vi.waitFor(() => expect(results).toHaveLength(2));
  expect(results[1]).toEqual({ chatId: "chat-1" });
  expect(api.createProjectChat).toHaveBeenCalledTimes(1);
});

it("starts a new thread for the slot's next message after a failed one was given up", async () => {
  const { take, results } = failingOnce();
  take({ id: "r1", key: "new:p1", send: send("m1") });
  await vi.waitFor(() => expect(results).toHaveLength(1));
  take({ id: "r2", key: "new:p1", send: send("m2") });
  await vi.waitFor(() => expect(results).toHaveLength(2));
  expect(results[1]).toEqual({ chatId: "chat-2" });
  expect(api.sendProjectChat).toHaveBeenLastCalledWith(
    "chat-2",
    expect.objectContaining({ id: "m2" }),
  );
});

it("attaches desktop-held draft audio to a phone's held send and clears it afterward", async () => {
  let take!: (request: HeldSend) => void;
  api.onDraftSend.mockImplementation((listener) => {
    take = listener;
    return () => {};
  });
  api.sendProjectChat.mockResolvedValueOnce(undefined);
  const results: HeldSendResult[] = [];
  api.finishDraftSend.mockImplementation((_id, result: HeldSendResult) =>
    results.push(result),
  );
  audio.set("chat-draft:t1", [
    {
      id: "clip-1",
      name: "memo.wav",
      mimeType: "audio/wav",
      sizeBytes: 4,
      durationSec: 2,
      peaks: [0.2, 0.9],
      dataUrl: "data:audio/wav;base64,UklGRg==",
    },
  ]);
  followHeldSends();
  take({
    id: "r-audio",
    key: "t1",
    chatId: "t1",
    send: {
      ...send("m-audio"),
      provider: "antigravity",
    },
  });
  await vi.waitFor(() => expect(results).toHaveLength(1));
  expect(results[0]).toEqual({ chatId: "t1" });
  expect(api.sendProjectChat).toHaveBeenCalledWith(
    "t1",
    expect.objectContaining({
      audio: [
        {
          name: "memo.wav",
          mimeType: "audio/wav",
          durationSec: 2,
          peaks: [0.2, 0.9],
          dataUrl: "data:audio/wav;base64,UklGRg==",
        },
      ],
    }),
  );
  expect(audio.get("chat-draft:t1")).toEqual([]);
});
