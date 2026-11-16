import { useState } from "react";
import type { ChatSummary } from "../../../shared/projects";

/** What a thread's menu asks before it snoozes: until when, from "Pick a time…". */
export function useSnoozeDialogs() {
  /** The thread whose snooze time is being picked. */
  const [picking, setPicking] = useState<ChatSummary>();
  return { picking, setPicking };
}
