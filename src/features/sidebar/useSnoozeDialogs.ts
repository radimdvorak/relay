import { useState } from "react";
import { liveAway } from "../../../shared/handoff";
import type { ChatSummary, ChatTriage } from "../../../shared/projects";

/**
 * What a thread's menu asks before it snoozes: until when, from "Pick a
 * time…", and for a thread on another computer, where.
 */
export function useSnoozeDialogs(
  apply: (c: ChatSummary, action: ChatTriage) => Promise<void>,
) {
  /** The thread whose snooze time is being picked. */
  const [picking, setPicking] = useState<ChatSummary>();
  /** A thread on another computer, and when it would wake. */
  const [away, setAway] = useState<{ chat: ChatSummary; until: number }>();
  return {
    picking,
    setPicking,
    away,
    /** Asks where first when the snoozed thread runs on another computer; true when it does. */
    asksWhere(c: ChatSummary, action: ChatTriage) {
      if (action.kind !== "snooze" || action.here || !liveAway(c)) return false;
      setAway({ chat: c, until: action.until });
      return true;
    },
    /** Snoozes the thread the question was for, on this computer alone or on its own too. */
    snoozeAway(here: boolean) {
      if (!away) return;
      setAway(undefined);
      void apply(away.chat, {
        kind: "snooze",
        until: away.until,
        ...(here ? { here: true as const } : {}),
      });
    },
    cancelAway: () => setAway(undefined),
  };
}
