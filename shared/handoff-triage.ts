import type { ChatSentTo } from "./handoff";
import type { ChatSummary, ChatTriage } from "./projects";

export type HandoffTriage = Pick<
  ChatSummary,
  | "updated"
  | "settledAt"
  | "settleWhenDone"
  | "unsettledAt"
  | "snoozedAt"
  | "snoozedUntil"
  | "autoSettled"
>;

/** Activity shelves follow the computer running the thread; archiving stays local. */
export function sharedHandoffTriage(action: ChatTriage) {
  switch (action.kind) {
    case "settle":
    case "settle-when-done":
    case "unsettle":
    case "snooze":
    case "wake":
      return true;
    case "restore":
      return action.from.archivedAt === action.to.archivedAt;
    default:
      return false;
  }
}

/** Keep the activity clock with its marks: a mirror may receive messages later. */
export function handoffTriage(chat: HandoffTriage): HandoffTriage {
  return {
    updated: chat.updated,
    settledAt: chat.settledAt,
    settleWhenDone: chat.settleWhenDone,
    unsettledAt: chat.unsettledAt,
    snoozedAt: chat.snoozedAt,
    snoozedUntil: chat.snoozedUntil,
    autoSettled: chat.autoSettled,
  };
}

/**
 * The marks a thread on another computer shows here: a snooze kept here
 * alone, until it's settled, moved back or snoozed over there after it.
 */
export function withSnoozeHere(
  triage: HandoffTriage,
  snoozedHere: ChatSentTo["snoozedHere"],
): HandoffTriage {
  if (!snoozedHere) return triage;
  const marksThere = [triage.settledAt, triage.unsettledAt, triage.snoozedAt];
  const changedThereSince = marksThere.some((at) => (at ?? 0) > snoozedHere.at);
  if (changedThereSince) return triage;
  return {
    ...triage,
    settledAt: undefined,
    settleWhenDone: undefined,
    autoSettled: undefined,
    snoozedAt: snoozedHere.at,
    snoozedUntil: snoozedHere.until,
  };
}
