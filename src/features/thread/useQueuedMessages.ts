import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  rootOf,
  type ChatMessage,
  type ProjectChat as ProjectChatData,
  type ProjectChatSend,
} from "../../../shared/projects";
import { recipient } from "../../../shared/recipient";
import { returnedDraft } from "../../../shared/returned-draft";
import { api } from "../../lib/api";
import { moveQueued, newestQueued, type QueueDrop } from "./chat-queue";
import { saveSentSettings } from "../agents/composer-settings";
import { loadDraftImages, saveDraftImages } from "../images/draft-images";
import { loadDraftAudio, saveDraftAudio } from "../audio/draft-audio";
import { readDraft, writeDraft } from "../composer/drafts";
import { threadDraftKey } from "../../lib/thread-storage";
import type { ComposerAttachments } from "./useComposerAttachments";
import type { ThreadHandle } from "./useThreadHandle";

export type QueuedMessageActions = ReturnType<typeof useQueuedMessages>;

/** Persists a queued message back into a composer draft before removing it, rolling back on failure. */
async function restoreQueuedDraft(
  chatId: string,
  key: string,
  input: ProjectChatSend,
  reply: boolean,
  hasSelection: boolean,
) {
  const old = readDraft(key);
  const [oldImages, oldAudio] = await Promise.all([
    loadDraftImages(key),
    loadDraftAudio(key),
  ]);
  const back = returnedDraft(
    old,
    oldImages,
    input,
    reply,
    () => crypto.randomUUID(),
    oldAudio,
  );
  if (input.selection && hasSelection)
    throw new Error(
      "Remove the current code selection before restoring this message.",
    );
  // Persist the complete draft before removing the durable queue entry.
  await Promise.all([
    saveDraftImages(key, back.images),
    saveDraftAudio(key, back.audio),
  ]);
  writeDraft(key, back.body);
  try {
    await api.projectChatQueueAction(chatId, "remove", input.id);
  } catch (e) {
    // It went out meanwhile: the draft goes back to what it was.
    writeDraft(key, old);
    await Promise.all([
      saveDraftImages(key, oldImages),
      saveDraftAudio(key, oldAudio),
    ]);
    throw e;
  }
  return back;
}

/** What can be done with a queued or scheduled message: send it now, move it, or take it back into the composer. */
export function useQueuedMessages({
  handle: { chat, id, busy, run, refetch, listChanged },
  messages,
  queue,
  attachments,
  onOpen,
}: {
  handle: ThreadHandle;
  messages: ChatMessage[];
  queue: ProjectChatData["queue"];
  attachments: ComposerAttachments;
  /** Opens the conversation a returned message belongs to: a side one by its first message, or the main one. */
  onOpen: (rootId: string | null) => void;
}) {
  const qc = useQueryClient();
  /** Counts messages taken back, so the composer remounts on the draft they left. */
  const [restored, setRestored] = useState(0);
  async function queueAction(
    action: "steer" | "move" | "remove",
    messageId: string,
    index?: number,
  ) {
    if (!chat) return;
    await run(async () => {
      await api.projectChatQueueAction(chat.id, action, messageId, index);
      await refetch();
      void listChanged();
    });
  }
  function move(moving: string, target: QueueDrop) {
    if (!chat || !queue) return;
    const moved = moveQueued(queue, moving, target);
    if (!moved) return;
    // Reorder right away; the refetch after the move confirms it.
    qc.setQueryData<ProjectChatData>(["project-chat", chat.id], (data) =>
      data?.queue ? { ...data, queue: moved.queue } : data,
    );
    void queueAction("move", moving, moved.index);
  }
  /** The conversation a queued message goes to: a side one by its first message, or the main one. */
  function conversationOf(input: ProjectChatSend) {
    const from = messages.find((m) => m.id === input.parentId);
    return from ? rootOf(messages, from).id : (input.parentId ?? null);
  }
  async function returnToComposer(input: ProjectChatSend) {
    if (!chat) return;
    await run(async () => {
      const parent = conversationOf(input);
      const key = threadDraftKey(id, parent);
      let back;
      try {
        back = await restoreQueuedDraft(
          chat.id,
          key,
          input,
          !!parent,
          !!attachments.selection,
        );
      } catch (e) {
        void refetch();
        throw e;
      }
      // A reply goes back to its side conversation, which keeps its own settings.
      saveSentSettings(
        parent ? `${id}:${parent}` : id,
        recipient(input),
        input,
      );
      attachments.addCodeRefs(back.codeRefs);
      if (input.selection) attachments.restoreSelection(input.selection);
      onOpen(parent);
      setRestored((n) => n + 1);
      await refetch();
      void listChanged();
    });
  }
  /** Takes the conversation's newest queued message back, as its × does; false when none waits. */
  function editLast(rootId: string | null) {
    if (!chat || busy) return false;
    const last = newestQueued(queue ?? [], rootId, conversationOf);
    if (!last) return false;
    void returnToComposer(last.input);
    return true;
  }
  return {
    restored,
    editLast,
    steer: (messageId: string) => queueAction("steer", messageId),
    remove: (messageId: string) => queueAction("remove", messageId),
    move,
    returnToComposer,
  };
}
