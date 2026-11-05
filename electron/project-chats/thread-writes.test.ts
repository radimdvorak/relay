import { it, expect, vi } from "vitest";
import { readFile, writeFile } from "node:fs/promises";
import { basename, join } from "node:path";
import { randomUUID } from "node:crypto";
import type { ChatMessage } from "../../shared/projects";
import {
  chats,
  projectId,
  input,
  restart,
  root,
  store,
  useProjectChats,
} from "../../tests/fixtures/project-chats-harness";

const written: { file: string; bytes: number }[] = [];

vi.mock("../util/atomic-write", async (importOriginal) => {
  const real = await importOriginal<typeof import("../util/atomic-write")>();
  return {
    ...real,
    writeFileAtomic: (...args: Parameters<typeof real.writeFileAtomic>) => {
      written.push({ file: basename(args[0]), bytes: args[1].length });
      return real.writeFileAtomic(...args);
    },
  };
});

vi.mock("../platform/executables");

useProjectChats();

/** A long thread's earlier turns, about 5MB of them. */
const earlierTurns = (): ChatMessage[] =>
  Array.from({ length: 2000 }, (_, i) => ({
    id: randomUUID(),
    role: i % 2 ? "assistant" : "user",
    body: "x".repeat(2500),
    status: "complete",
    created: i,
    version: 1,
    provider: "codex",
  }));

it("rewrites a long thread's file whole only at the end of a turn and the session's start", async () => {
  const made = await chats.create(projectId, { kind: "project" });
  await chats.dispose();
  const file = join(root, "chats", `${made.id}.json`);
  const saved = JSON.parse(await readFile(file, "utf8"));
  saved.messages = earlierTurns();
  await writeFile(file, JSON.stringify(saved));
  restart();
  written.length = 0;

  await chats.send(made.id, input("@codex Explain the cache guard"));
  await vi.waitFor(async () => {
    const thread = await chats.get(made.id);
    expect(thread.messages.at(-1)?.status).toBe("complete");
    expect(chats.hasActiveProject(projectId)).toBe(false);
  });
  await store.flush();

  const threadFile = written.filter((w) => w.file === `${made.id}.json`);
  const liveFile = written.filter((w) => w.file === `${made.id}.live.json`);
  const stateFile = written.filter((w) => w.file === "state.json");
  // Was 4: the sent message and the answer's start each rewrote it too.
  expect(threadFile).toHaveLength(2);
  expect(liveFile).toHaveLength(2);
  for (const { bytes } of liveFile)
    expect(bytes).toBeLessThan(threadFile[0].bytes / 20);
  // Was 3; the summary's changes as the turn starts and ends are written together.
  expect(stateFile.length).toBeLessThanOrEqual(2);
});
