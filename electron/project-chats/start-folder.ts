// Agents whose file tools reach only the folder their session opened in,
// like Antigravity's: moving the thread mid-answer leaves them outside it
// until a new session opens there, so Relay carries them on in one.

import { randomUUID } from "node:crypto";
import { agentInfo, agentName } from "../../shared/agents";
import type {
  AgentProvider,
  ProjectChat,
  ProjectChatSend,
} from "../../shared/projects";
import { recipient } from "../../shared/recipient";

export const heldToStartFolder = (provider: AgentProvider) =>
  !!agentInfo(provider).heldToStartFolder;

/**
 * What a move's tool result tells an agent held to its start folder: it
 * can't work in `folder` before this answer ends, so it should end it.
 */
export function endAnswerAfterMove(
  provider: AgentProvider | undefined,
  folder: string,
) {
  if (!provider || !heldToStartFolder(provider)) return undefined;
  return [
    `${agentName(provider)}'s file tools only reach the folder this session opened in, so they can't reach ${folder} during this answer.`,
    "End your answer now, in a sentence saying you're carrying on there.",
    "Relay then starts you again in that folder with a message to carry on, and you finish the work from it.",
  ].join(" ");
}

/** A message already waiting for the agent opens its next session in the new folder anyway. */
function agentHasQueued(chat: ProjectChat, provider: AgentProvider) {
  return !!chat.queue?.some(
    (q) => !q.compact && !q.input.parentId && recipient(q.input) === provider,
  );
}

/**
 * Puts the message that carries the agent on in `folder` at the front of
 * the queue, on the settings of the answer that moved there. Signed with
 * the thread's own id, like a wake-up, so the agent reads it as its own.
 */
export function queueCarryOn(
  chat: ProjectChat,
  answered: ProjectChatSend,
  provider: AgentProvider,
  folder: string,
) {
  if (agentHasQueued(chat, provider)) return;
  const input: ProjectChatSend = {
    id: randomUUID(),
    body: `You work in ${folder} now, and your file tools reach it. Carry on with what you were doing there; if it's already done, say so briefly.`,
    choice: answered.choice,
    ...(answered.contextWindow
      ? { contextWindow: answered.contextWindow }
      : {}),
    ...(answered.advisor ? { advisor: answered.advisor } : {}),
    provider,
    to: provider,
    runtimeMode: answered.runtimeMode,
    interactionMode: answered.interactionMode,
    fromThread: { id: chat.id, name: "Relay" },
  };
  chat.queue = [{ input, created: Date.now() }, ...(chat.queue ?? [])];
}
