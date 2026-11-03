import type { ProjectChat, ProjectChatSend } from "../../shared/projects";

/**
 * What a thread's agent is told when another thread's agent wrote this
 * turn's message: who wrote it, and that its answer doesn't reach that
 * thread by itself. Messages from its lead or from threads it started come
 * with notes of their own, and a reviewer only hears from its review.
 */
export function peerNote(
  chat: ProjectChat,
  input: ProjectChatSend,
  /** It has send_to_thread. */
  canSend: boolean,
) {
  const sender = input.fromThread;
  if (!sender || chat.reviewer) return undefined;
  const ownWakeUp = sender.id === chat.id;
  const fromStartedThread = !!sender.child;
  const fromLead = sender.id === chat.startedBy?.chatId;
  if (ownWakeUp || fromStartedThread || fromLead) return undefined;
  return [
    `This message is from another thread's agent, not the user: ${sender.name} (thread ${sender.id}).`,
    "Your answer stays in this thread, where only the user reads it; that thread doesn't hear it.",
    canSend
      ? `To answer that thread, use send_to_thread with id ${sender.id}.`
      : "You can't message that thread, so say here what it should know and the user can pass it on.",
  ].join(" ");
}
