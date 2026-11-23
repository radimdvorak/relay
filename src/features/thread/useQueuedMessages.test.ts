// @vitest-environment happy-dom
import { expect, it, vi } from "vitest";
import { createElement } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { flush, mount, update } from "../../../tests/unit/hook-harness";
import type {
  ChatSummary,
  ProjectChat,
  ProjectChatSend,
} from "../../../shared/projects";

const api = vi.hoisted(() => ({
  projectChatQueueAction: vi.fn(async (..._: unknown[]) => {}),
}));

vi.mock("../../lib/api", () => ({ api }));

const audioStore = vi.hoisted(() => new Map<string, unknown[]>());

vi.mock("../images/draft-images", () => ({
  loadDraftImages: async () => [],
  saveDraftImages: async () => {},
  draftImageCount: () => 0,
  draftsWithImages: () => [],
  readDraftImageCounts: async () => {},
  onDraftImageCount: () => () => {},
}));

vi.mock("../audio/draft-audio", () => ({
  loadDraftAudio: async (key: string) => audioStore.get(key) ?? [],
  saveDraftAudio: async (key: string, list: unknown[]) =>
    void audioStore.set(key, list),
  draftAudioCount: (key: string) => audioStore.get(key)?.length ?? 0,
  draftsWithAudio: () => [...audioStore.keys()],
  readDraftAudioCounts: async () => {},
  onDraftAudioCount: () => () => {},
}));

const { useThreadHandle } = await import("./useThreadHandle");
const { useQueuedMessages } = await import("./useQueuedMessages");
const { readDraft, writeDraft } = await import("../composer/drafts");

const chat = { id: "t1" } as ChatSummary;

const attachments = {
  selection: undefined,
  addCodeRefs: () => {},
} as unknown as Parameters<typeof useQueuedMessages>[0]["attachments"];

const queued = (
  id: string,
  body: string,
  created: number,
  extra: Partial<ProjectChatSend> = {},
) => ({
  input: { id, body, provider: "claude", ...extra } as ProjectChatSend,
  created,
});

it("takes back the newest queued message, not a compaction queued after it", async () => {
  // A compaction asked for while a message waits goes ahead of it, newest.
  const queue: ProjectChat["queue"] = [
    { ...queued("c1", "/compact", 2), compact: true },
    queued("m1", "and then tests", 1),
  ];
  const client = new QueryClient();
  const hooks = mount(
    () => {
      const handle = useThreadHandle(chat, chat.id, "p1", async () => {});
      return useQueuedMessages({
        handle,
        messages: [],
        queue,
        attachments,
        onOpen: () => {},
      });
    },
    (child) => createElement(QueryClientProvider, { client }, child),
  );
  update(() => hooks().editLast(null));
  await flush();
  expect(api.projectChatQueueAction).toHaveBeenCalledWith("t1", "remove", "m1");
  expect(readDraft("chat-draft:t1")).toBe("and then tests");
});

it("restores queued audio into the draft and rolls back if removing the queue item fails", async () => {
  writeDraft("chat-draft:t1", "");
  audioStore.clear();
  const clip = {
    name: "voice.wav",
    mimeType: "audio/wav" as const,
    durationSec: 1.5,
    dataUrl: "data:audio/wav;base64,UklGRg==",
  };
  const queue: ProjectChat["queue"] = [
    queued("m-audio", "@antigravity listen", 1, {
      provider: "antigravity",
      audio: [clip],
    }),
  ];
  const client = new QueryClient();
  const hooks = mount(
    () => {
      const handle = useThreadHandle(chat, chat.id, "p1", async () => {});
      return useQueuedMessages({
        handle,
        messages: [],
        queue,
        attachments,
        onOpen: () => {},
      });
    },
    (child) => createElement(QueryClientProvider, { client }, child),
  );

  api.projectChatQueueAction.mockRejectedValueOnce(new Error("Already sent"));
  update(() => hooks().editLast(null));
  await flush();
  expect(audioStore.get("chat-draft:t1")).toEqual([]);
  expect(readDraft("chat-draft:t1")).toBe("");

  update(() => hooks().editLast(null));
  await flush();
  expect(audioStore.get("chat-draft:t1")).toEqual([
    expect.objectContaining({
      name: "voice.wav",
      mimeType: "audio/wav",
      durationSec: 1.5,
      sizeBytes: 4,
    }),
  ]);
});
