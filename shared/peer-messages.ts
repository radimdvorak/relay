// Messages a thread's Claude session sent to or got from another Claude
// session on this computer with Claude Code's SendMessage, as Relay saw
// them go out. Neither side's transcript shows them to the user otherwise.

/** The other session: its Claude Code name, and its thread when it's one of Relay's. */
export interface PeerRef {
  name: string;
  threadId?: string;
}

export interface PeerMessage {
  id: string;
  direction: "in" | "out";
  peer: PeerRef;
  text: string;
  at: number;
}

/** A thread keeps its latest ones; the strip only shows the latest exchange. */
const KEPT = 40;
export const PEER_TEXT_LIMIT = 2000;

/** How soon after a message comes in a turn Claude starts by itself counts as its answer. */
const WAKE_WINDOW = 60_000;

export function withPeerMessage(
  list: PeerMessage[] | undefined,
  message: PeerMessage,
) {
  return [...(list ?? []), message].slice(-KEPT);
}

/**
 * Who started a turn Claude began by itself at `at`: the latest message in,
 * when it came just before and after the thread's last answer ended. One
 * that came mid-answer was read there and woke nothing.
 */
export function wokenBy(
  list: PeerMessage[] | undefined,
  at: number,
  lastAnswerEnded = 0,
): PeerRef | undefined {
  const last = list?.at(-1);
  if (last?.direction !== "in") return undefined;
  const cameWhileIdle = last.at >= lastAnswerEnded;
  const justBefore = Math.abs(at - last.at) <= WAKE_WINDOW;
  return cameWhileIdle && justBefore ? last.peer : undefined;
}

export interface PeerMessagesApi {
  /** What the thread's session and other sessions told each other, oldest first. */
  threadPeerMessages(id: string): Promise<PeerMessage[]>;
  /** Tells both threads they went quiet after messaging each other, as the user. */
  nudgePeerThreads(id: string, peerId: string): Promise<void>;
}
