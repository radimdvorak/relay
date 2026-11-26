// The steps an agent set itself, which the app shows as its progress, and the
// checklist a thread shows as its own. Not a plan to approve: that is Plan mode.
import { threadOrder, type ChatMessage } from "./projects";

export interface ChecklistStep {
  text: string;
  state: "next" | "now" | "done" | "failed";
}

const limits = { steps: 100, text: 300 };

/** An agent's steps as a row keeps them; undefined when there are none. */
export function keptSteps(steps: ChecklistStep[]): ChecklistStep[] | undefined {
  const named = steps
    .map((step) => ({ ...step, text: step.text.trim().slice(0, limits.text) }))
    .filter((step) => step.text)
    .slice(0, limits.steps);
  return named.length ? named : undefined;
}

const isOver = (step: ChecklistStep) =>
  step.state === "done" || step.state === "failed";

export const checklistIsOver = (steps: ChecklistStep[]) => steps.every(isOver);

/** Where the checklist stands, as "3 of 5": the step it's on counts. */
export function checklistProgress(steps: ChecklistStep[]) {
  const over = steps.filter(isOver).length;
  const working = steps.some((step) => step.state === "now");
  return { at: working ? over + 1 : over, of: steps.length };
}

/** The step the agent is on, or else the first it hasn't reached. */
export const currentStep = (steps: ChecklistStep[]) =>
  steps.find((step) => step.state === "now") ??
  steps.find((step) => step.state === "next");

/** The checklist an answer last left. A subagent's checklist is its own. */
function checklistOf(message: ChatMessage): ChecklistStep[] | undefined {
  let checklist: ChecklistStep[] | undefined;
  for (const entry of message.trace ?? [])
    if (entry.kind === "activity" && !entry.activity.parentId)
      checklist = entry.activity.checklist ?? checklist;
  return checklist;
}

/** An agent's finished answer, not a row Relay or a compaction left between answers. */
const isFinishedAnswer = (m: ChatMessage) =>
  m.role === "assistant" &&
  m.status === "complete" &&
  !m.compaction &&
  !m.handoff &&
  !m.reload &&
  !m.worktreeCommand;

/**
 * The thread's own conversation: no `/btw` question, and no reply, even one
 * whose parent a phone doesn't hold.
 */
const ownConversation = (messages: ChatMessage[]) =>
  [...messages].sort(threadOrder).filter((m) => !m.parentId && !m.side);

/**
 * The checklist a thread's main conversation is on. A finished one stays until the
 * next message. An unfinished one carries over to the next turn, until an
 * answer ends without having touched it: the agent has moved on.
 */
export function threadChecklist(messages: ChatMessage[]): ChecklistStep[] | undefined {
  let askedSince = false;
  for (const message of ownConversation(messages).reverse()) {
    const checklist = checklistOf(message);
    if (checklist) {
      const emptied = !checklist.length;
      const putAway = askedSince && checklistIsOver(checklist);
      return emptied || putAway ? undefined : checklist;
    }
    if (isFinishedAnswer(message)) return undefined;
    if (message.role === "user") askedSince = true;
  }
  return undefined;
}
