import {
  root,
  repo,
  chats,
  projectId,
  useProjectChats,
} from "../../tests/fixtures/project-chats-harness";
// An agent held to the folder its session opened in, like Antigravity, moves
// its thread mid-answer. The fake Codex stands in for it: it calls Relay's
// tools over MCP like a real agent, and here counts as held to its folder.
import { it, expect, vi, beforeEach, afterEach } from "vitest";
import { writeFile, readFile } from "node:fs/promises";
import { join } from "node:path";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { defaultAISettings } from "../../shared/settings";
import type { AgentProvider } from "../../shared/projects";
import { StartedThreads } from "../started-threads";
import { serveRelayTools, useRelayMcp, verifyRelayToken } from "../relay-mcp";
import type { WorktreeRequest } from "./enter-worktree";

vi.mock("../platform/executables");

const held = vi.hoisted(() => ({ providers: new Set<string>(["codex"]) }));

vi.mock("../../shared/agents", async (original) => {
  const real = await original<typeof import("../../shared/agents")>();
  return {
    ...real,
    agentInfo: (provider: AgentProvider) => {
      const info = real.agentInfo(provider);
      return held.providers.has(provider)
        ? { ...info, heldToStartFolder: true }
        : info;
    },
  };
});

const secret = "c".repeat(64);
let closeTools: () => Promise<void>;
const git = (...args: string[]) =>
  execFileSync("git", ["-C", repo, ...args], { encoding: "utf8" });

useProjectChats({
  prepare: async () => {
    git("branch", "-m", "main");
    await writeFile(join(repo, "README.md"), "Hello\n");
    git("add", ".");
    git(
      "-c",
      "user.name=T",
      "-c",
      "user.email=t@example.invalid",
      "commit",
      "-qm",
      "Initial",
    );
  },
});

beforeEach(async () => {
  held.providers = new Set(["codex"]);
  const serving = serveRelayTools(0, {
    verify: (token) => verifyRelayToken(secret, token),
    call: new StartedThreads(chats).handle,
  });
  await serving.ready;
  closeTools = serving.close;
  const { port } = serving.server.address() as { port: number };
  useRelayMcp({ port, secret });
});

afterEach(async () => {
  useRelayMcp(undefined);
  await closeTools?.();
});

const input = (body: string) => ({
  id: randomUUID(),
  body,
  provider: "codex" as const,
  runtimeMode: "full-access" as const,
  interactionMode: "default" as const,
  choice: { ...defaultAISettings.questions, model: "fixture-model" },
});

const moveTo = (request: WorktreeRequest) =>
  input(`@codex fixture relay move_to_worktree ${JSON.stringify(request)}`);

async function settled(id: string, count: number) {
  await vi.waitFor(async () => {
    const chat = await chats.get(id);
    expect(chat.messages).toHaveLength(count);
    expect(chat.messages.at(-1)?.status).not.toBe("streaming");
  });
  return chats.get(id);
}

/**
 * A message sent after the answer ended queues behind anything Relay queued
 * as it ended, so once it's answered nothing else was waiting.
 */
async function nothingCarriedOn(id: string, count: number) {
  await chats.send(id, input("@codex fixture echo: next"));
  const after = await settled(id, count + 2);
  expect(after.messages.at(-2)?.body).toContain("next");
  expect(after.messages.some((m) => m.author === "Relay")).toBe(false);
  expect(after.queue ?? []).toEqual([]);
}

async function lastCwd() {
  const calls = (await readFile(join(root, "capture.jsonl"), "utf8"))
    .split("\n")
    .filter(Boolean)
    .map((line) => JSON.parse(line));
  return calls.filter((c) => c.thread).at(-1).thread.cwd;
}

it("carries the agent on in the worktree without the user sending anything", async () => {
  const chat = await chats.create(projectId, { kind: "project" });
  await chats.send(chat.id, moveTo({ branch: "feature" }));

  const done = await settled(chat.id, 4);
  const folder = done.worktree!.path!;
  // The tool told the agent to end its answer instead of trying its file tools.
  expect(done.messages[1].body).toContain("endThisAnswer");
  const carryOn = done.messages[2];
  expect(carryOn).toMatchObject({ role: "user", author: "Relay" });
  expect(carryOn.body).toContain(folder);
  expect(done.messages[3].status).toBe("complete");
  expect(await lastCwd()).toBe(folder);
  expect(done.queue ?? []).toEqual([]);
});

it("leaves an agent that can follow the move alone", async () => {
  held.providers = new Set();
  const chat = await chats.create(projectId, { kind: "project" });
  await chats.send(chat.id, moveTo({ branch: "feature" }));

  const done = await settled(chat.id, 2);
  expect(done.messages[1].body).not.toContain("endThisAnswer");
  await nothingCarriedOn(chat.id, 2);
});

it("doesn't carry on an answer the user stopped", async () => {
  const entry = chats.worktrees.entry;
  const enter = entry.enter.bind(entry);
  let release!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  let reached!: () => void;
  const atTool = new Promise<void>((resolve) => (reached = resolve));
  vi.spyOn(entry, "enter").mockImplementation(async (id, request) => {
    reached();
    await gate;
    return enter(id, request);
  });
  const chat = await chats.create(projectId, { kind: "project" });
  await chats.send(chat.id, moveTo({ branch: "feature" }));
  await atTool;
  await chats.cancel(chat.id);
  release();

  const done = await settled(chat.id, 2);
  expect(done.messages[1].status).toBe("cancelled");
  await nothingCarriedOn(chat.id, 2);
});

it("lets a message the user queued for the agent start it in the worktree instead", async () => {
  const entry = chats.worktrees.entry;
  const enter = entry.enter.bind(entry);
  let release!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  let reached!: () => void;
  const atTool = new Promise<void>((resolve) => (reached = resolve));
  vi.spyOn(entry, "enter").mockImplementation(async (id, request) => {
    reached();
    await gate;
    return enter(id, request);
  });
  const chat = await chats.create(projectId, { kind: "project" });
  await chats.send(chat.id, moveTo({ branch: "feature" }));
  await atTool;
  await chats.send(chat.id, input("@codex fixture echo: and the tests"));
  release();

  const done = await settled(chat.id, 4);
  const folder = done.worktree!.path!;
  expect(done.messages[2].body).toContain("and the tests");
  expect(done.messages.some((m) => m.author === "Relay")).toBe(false);
  expect(await lastCwd()).toBe(folder);
});

it("carries on once: the next answer moving nowhere new runs nothing more", async () => {
  const chat = await chats.create(projectId, { kind: "project" });
  await chats.send(chat.id, moveTo({ branch: "feature" }));
  await settled(chat.id, 4);
  // Already in a worktree: the tool refuses, so nothing moves or carries on.
  await chats.send(chat.id, moveTo({ branch: "other" }));

  const done = await settled(chat.id, 6);
  expect(done.messages[5].body).toContain("already works in its own worktree");
  await chats.send(chat.id, input("@codex fixture echo: next"));
  const after = await settled(chat.id, 8);
  expect(after.messages[6].body).toContain("next");
  expect(after.messages.filter((m) => m.author === "Relay")).toHaveLength(1);
});
