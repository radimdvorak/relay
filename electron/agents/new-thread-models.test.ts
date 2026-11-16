import { afterEach, expect, it } from "vitest";
import { mkdtemp, rm } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { randomUUID } from "node:crypto";
import { Store } from "../app/store";
import {
  newThreadModels,
  rememberSent,
  saveNewThreadModel,
} from "./new-thread-models";
import type { ProjectChatSend } from "../../shared/projects";

const dirs: string[] = [];

afterEach(async () => {
  await Promise.all(dirs.splice(0).map((d) => rm(d, { recursive: true })));
});

async function store() {
  const dir = await mkdtemp(join(tmpdir(), "relay-new-thread-"));
  dirs.push(dir);
  const s = new Store(dir);
  await s.load();
  return s;
}

const send = (changes: Partial<ProjectChatSend>): ProjectChatSend => ({
  id: randomUUID(),
  body: "@claude look",
  to: "claude",
  provider: "claude",
  choice: { model: "opus", fast: false, reasoningEffort: "max" },
  runtimeMode: "read-only",
  interactionMode: "default",
  ...changes,
});

it("makes a send's model and access the next new thread's, but not a note's", async () => {
  const s = await store();
  await rememberSent(s, send({}));
  expect(s.get().newThreadRuntimeMode).toBe("read-only");
  expect(s.get().newThreadModels?.claude?.choice.model).toBe("opus");
  // A note to the people in the thread ran on no agent.
  await rememberSent(
    s,
    send({ body: "for later", to: "message", runtimeMode: "full-access" }),
  );
  expect(s.get().newThreadRuntimeMode).toBe("read-only");
});

it("never hands an advisor on to new threads, here or on the phone", async () => {
  const s = await store();
  const fable = { provider: "claude" as const, model: "fable" };
  await rememberSent(s, send({ advisor: fable }));
  expect(s.get().newThreadModels?.claude).toEqual({
    choice: send({}).choice,
  });
  await saveNewThreadModel(s, "codex", {
    choice: { model: "", fast: false, reasoningEffort: "" },
    advisor: fable,
  });
  expect(s.get().newThreadModels?.codex?.advisor).toBeUndefined();
  // Kept before advisors stopped carrying over.
  await s.update((state) => {
    state.newThreadModels = {
      claude: { choice: send({}).choice, advisor: fable },
    };
  });
  expect(newThreadModels(s).claude?.advisor).toBeUndefined();
});
