// Above the composer while the thread's Claude session is in a conversation
// with another session on this computer: who with, where it stands, and
// what they said. Both threads stopping right after it offers a nudge.
import { memo, useState } from "react";
import { MessagesSquare } from "lucide-react";
import type { PeerMessage, PeerRef } from "../../../shared/peer-messages";
import type { ChatMessage, ChatSummary } from "../../../shared/projects";
import { api } from "../../lib/api";
import { useNow } from "../../lib/useNow";
import { useStartedThreads } from "../agent-turn/StartedThreads";
import { currentTalk, talkState, type TalkState } from "./peer-talk";
import { usePeerMessages } from "./usePeerMessages";
import "./peer-strip.css";

const clock = (at: number) =>
  new Date(at).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });

function minutesAgo(since: number, now: number) {
  const minutes = Math.round((now - since) / 60_000);
  return minutes < 1 ? "just now" : `${minutes}m ago`;
}

function stateLabel(state: TalkState, now: number) {
  switch (state.kind) {
    case "working":
      if (state.who === "both") return "both working";
      return state.who === "this" ? "this one's working" : "it's working";
    case "asking":
      return "waiting on you";
    case "quiet":
      return `last word ${minutesAgo(state.since, now)}`;
    case "stalled":
      return `both idle since ${clock(state.since)}`;
  }
}

/** The other thread's summary and how to open it, when it's one of Relay's. */
function usePeerThread(peer: PeerRef | undefined) {
  const view = useStartedThreads();
  const summary = peer?.threadId
    ? view?.threads.find((c) => c.id === peer.threadId)
    : undefined;
  return {
    summary,
    label: summary?.title ?? peer?.name ?? "",
    open: summary && view ? () => view.open(summary) : undefined,
  };
}

function PeerStripView({
  chat,
  messages,
}: {
  chat: ChatSummary;
  messages: ChatMessage[];
}) {
  const peerMessages = usePeerMessages(chat);
  const now = useNow(15_000, !!peerMessages?.length);
  const talk = currentTalk(peerMessages, now);
  const peer = usePeerThread(talk?.peer);
  const [open, setOpen] = useState(false);
  const [nudged, setNudged] = useState<string>();
  if (!talk) return null;
  const userSince = messages.some(
    (m) => m.role === "user" && m.created > talk.last.at,
  );
  const state = talkState(talk, chat, peer.summary, userSince, now);
  const stalled = state.kind === "stalled";
  const peerId = talk.peer.threadId;
  const canNudge = stalled && !!peerId && nudged !== talk.last.id;
  const nudge = () => {
    if (!peerId) return;
    setNudged(talk.last.id);
    api.nudgePeerThreads(chat.id, peerId).catch(() => setNudged(undefined));
  };
  return (
    <section className="peer-strip" aria-label="Talking with another thread">
      <div className="peer-strip-head">
        <MessagesSquare size={14} aria-hidden />
        <span className="peer-strip-who">
          Talking with{" "}
          {peer.open ? (
            <button
              type="button"
              className="peer-strip-peer"
              onClick={peer.open}
            >
              {peer.label}
            </button>
          ) : (
            <span className="peer-strip-peer">{peer.label}</span>
          )}
        </span>
        <span className="peer-strip-state" data-stalled={stalled || undefined}>
          {stateLabel(state, now)}
        </span>
        <span className="peer-strip-space" />
        {canNudge && (
          <button type="button" className="peer-strip-button" onClick={nudge}>
            Nudge both
          </button>
        )}
        <button
          type="button"
          className="peer-strip-button"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          {talk.messages.length === 1
            ? "1 message"
            : `${talk.messages.length} messages`}
        </button>
      </div>
      {open && <PeerLog messages={talk.messages} peer={peer.label} />}
    </section>
  );
}

function PeerLog({
  messages,
  peer,
}: {
  messages: PeerMessage[];
  peer: string;
}) {
  return (
    <ol className="peer-strip-log">
      {messages.map((m) => (
        <li key={m.id}>
          <time>{clock(m.at)}</time>
          <span>
            <b>{m.direction === "out" ? "→ This thread" : `← ${peer}`}:</b>{" "}
            {m.text}
          </span>
        </li>
      ))}
    </ol>
  );
}

/** When the user last wrote in the thread; 0 before they have. */
function lastUserMessageAt(messages: ChatMessage[]) {
  for (let i = messages.length - 1; i >= 0; i--)
    if (messages[i].role === "user") return messages[i].created;
  return 0;
}

// Every streamed piece hands over new messages, but the strip only reads when
// the user last wrote, so it skips the pieces.
export const PeerStrip = memo(
  PeerStripView,
  (before, after) =>
    before.chat === after.chat &&
    lastUserMessageAt(before.messages) === lastUserMessageAt(after.messages),
);
