// What a thread's Claude session and other Claude sessions on this computer
// tell each other with SendMessage. Relay sees a message as it goes out, so
// it keeps it on the sending thread and, when the session it went to is
// another of Relay's threads, on that one too.
import { randomUUID } from "node:crypto";
import {
  PEER_TEXT_LIMIT,
  withPeerMessage,
  wokenBy,
  type PeerMessage,
} from "../../shared/peer-messages";
import type {
  AgentProvider,
  ChatMessage,
  ProjectChat,
  ProjectChatSend,
} from "../../shared/projects";
import { sentAgent } from "../../shared/recipient";
import { accountHomes } from "../agents/accounts";
import { claudeSessionKey } from "../agents/claude/project";
import { addressee, listedSessions } from "../peer-messages";
import type { Store } from "../app/store";
import type { ChatCore } from "./core";
import { agentSession, parseSessionKey, sessionInput } from "./sessions";

const claudeHomes = () =>
  accountHomes()
    .filter((h) => h.provider === "claude")
    .map((h) => h.home);

/** When the thread's last answer ended, before the one being written. */
export function lastAnswerEnded(messages: ChatMessage[]) {
  for (let i = messages.length - 1; i >= 0; i--) {
    const m = messages[i]!;
    if (m.role === "assistant" && m.ended) return m.ended;
  }
  return 0;
}

export class PeerSends {
  constructor(
    private core: ChatCore,
    private homes = claudeHomes,
  ) {}

  /** `chat`'s session sent a message; `send.to` is who it addressed. */
  async sent(
    chat: ProjectChat,
    provider: AgentProvider,
    replyTo: string | undefined,
    send: { to: string; message: string },
  ) {
    const sessions = await listedSessions(this.homes());
    const recipient = addressee(sessions, send.to);
    // Not another session: a subagent, a teammate or `main`.
    if (!recipient) return;
    const ownId = agentSession(chat, provider, replyTo).thread;
    const own = sessions.find((s) => s.sessionId === ownId);
    const key = claudeSessionKey(recipient.sessionId);
    const recipientChat = key ? parseSessionKey(key).chatId : undefined;
    const at = Date.now();
    const text = send.message.slice(0, PEER_TEXT_LIMIT);
    await this.record(chat, {
      id: randomUUID(),
      direction: "out",
      peer: {
        name: recipient.name,
        ...(recipientChat ? { threadId: recipientChat } : {}),
      },
      text,
      at,
    });
    if (!recipientChat || recipientChat === chat.id) return;
    const thread = await this.core.storage
      .load(recipientChat)
      .catch(() => undefined);
    if (!thread) return;
    await this.record(thread, {
      id: randomUUID(),
      direction: "in",
      peer: { name: own?.name ?? chat.title, threadId: chat.id },
      text,
      at,
    });
  }

  private async record(chat: ProjectChat, message: PeerMessage) {
    chat.peerMessages = withPeerMessage(chat.peerMessages, message);
    const woken = message.direction === "in" && this.markWoken(chat);
    await this.core.storage.save(chat);
    if (woken)
      this.core.emit({ chatId: chat.id, message: structuredClone(woken) });
  }

  /**
   * The turn the message woke may have started before Relay heard it was
   * sent: the sender only learns it went out once it's there.
   */
  private markWoken(chat: ProjectChat) {
    const turn = chat.messages.at(-1);
    if (!turn?.unprompted || turn.status !== "streaming" || turn.wokenBy)
      return undefined;
    const earlier = chat.messages.slice(0, -1);
    const by = wokenBy(
      chat.peerMessages,
      turn.created,
      lastAnswerEnded(earlier),
    );
    if (!by) return undefined;
    turn.wokenBy = by;
    return turn;
  }
}

/** One `PeerSends` per data folder's threads. */
const recorders = new WeakMap<ChatCore, PeerSends>();

export function peerSendsOf(core: ChatCore) {
  let recorder = recorders.get(core);
  if (!recorder) recorders.set(core, (recorder = new PeerSends(core)));
  return recorder;
}

/** What the user's "Nudge both" sends `thread` about the one it went quiet with. */
export function nudgeFor(
  thread: ProjectChat,
  other: ProjectChat,
  store: Pick<Store, "aiSettings">,
): ProjectChatSend {
  const provider = thread.lastInput ? sentAgent(thread.lastInput) : "claude";
  return {
    ...sessionInput(thread, provider, store),
    body: [
      `You and “${other.title}” both went quiet after messaging each other.`,
      "If one of you is waiting on the other, check where things stand with it and carry on.",
    ].join(" "),
  };
}
