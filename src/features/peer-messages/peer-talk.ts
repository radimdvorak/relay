// Which exchange with another Claude session the thread shows, and where it
// stands: someone's working, it's gone quiet, or both threads stopped right
// after talking, which usually means each is waiting on the other.
import type { PeerMessage, PeerRef } from "../../../shared/peer-messages";

/** An exchange stops showing this long after its last message. */
const SHOWN_FOR = 30 * 60_000;
/** Quiet this long after both stopped reads as stuck. */
const STALL_AFTER = 2 * 60_000;
/** A thread that stopped this soon after the last message stopped because of it. */
const STOPPED_ON_IT = 3 * 60_000;

export interface PeerTalk {
  peer: PeerRef;
  messages: PeerMessage[];
  last: PeerMessage;
}

/** Where a thread is, as its listed summary says. */
export interface ThreadNow {
  running?: boolean;
  asking?: boolean;
  updated: number;
}

export type TalkState =
  | { kind: "working"; who: "this" | "peer" | "both" }
  | { kind: "asking" }
  | { kind: "quiet"; since: number }
  | { kind: "stalled"; since: number };

const samePeer = (a: PeerRef, b: PeerRef) =>
  a.threadId && b.threadId ? a.threadId === b.threadId : a.name === b.name;

/**
 * The exchange with whoever the latest message was with, while it's recent.
 * One-way traffic doesn't count: a broadcast ("anyone using the editor?")
 * this thread never answered isn't a conversation it's in.
 */
export function currentTalk(
  messages: PeerMessage[] | undefined,
  now: number,
): PeerTalk | undefined {
  const last = messages?.at(-1);
  if (!messages || !last || now - last.at > SHOWN_FOR) return undefined;
  const withPeer = messages.filter((m) => samePeer(m.peer, last.peer));
  const answered =
    withPeer.some((m) => m.direction === "in") &&
    withPeer.some((m) => m.direction === "out");
  if (!answered) return undefined;
  return { peer: last.peer, messages: withPeer, last };
}

/**
 * Where the exchange stands. `peer` is the other thread's summary, when it's
 * one of Relay's; `userSince` says the user wrote here after the last message.
 */
export function talkState(
  talk: PeerTalk,
  own: ThreadNow,
  peer: ThreadNow | undefined,
  userSince: boolean,
  now: number,
): TalkState {
  if (own.running && peer?.running) return { kind: "working", who: "both" };
  if (own.running) return { kind: "working", who: "this" };
  if (peer?.running) return { kind: "working", who: "peer" };
  if (own.asking || peer?.asking) return { kind: "asking" };
  const since = Math.max(talk.last.at, own.updated, peer?.updated ?? 0);
  const stoppedOnIt = (thread: ThreadNow) =>
    thread.updated - talk.last.at <= STOPPED_ON_IT;
  const bothStoppedOnIt = !!peer && stoppedOnIt(own) && stoppedOnIt(peer);
  const quietLongEnough = now - since >= STALL_AFTER;
  if (bothStoppedOnIt && quietLongEnough && !userSince)
    return { kind: "stalled", since };
  return { kind: "quiet", since };
}
