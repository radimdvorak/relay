// The steps an agent set itself, and which plan a thread shows as its own.
import {
  mainConversation,
  threadOrder,
  type ChatMessage,
} from "./projects";

export interface PlanStep {
  text: string;
  state: "next" | "now" | "done" | "failed";
}

const limits = { steps: 100, text: 300 };

/** An agent's steps as a row keeps them; undefined when there are none. */
export function keptSteps(steps: PlanStep[]): PlanStep[] | undefined {
  const named = steps
    .map((step) => ({ ...step, text: step.text.trim().slice(0, limits.text) }))
    .filter((step) => step.text)
    .slice(0, limits.steps);
  return named.length ? named : undefined;
}

const isOver = (step: PlanStep) =>
  step.state === "done" || step.state === "failed";

export const planIsOver = (steps: PlanStep[]) => steps.every(isOver);

/** Where the plan stands, as "3 of 5": the step it's on counts. */
export function planProgress(steps: PlanStep[]) {
  const over = steps.filter(isOver).length;
  const working = steps.some((step) => step.state === "now");
  return { at: working ? over + 1 : over, of: steps.length };
}

/** The step the agent is on, or else the first it hasn't reached. */
export const currentStep = (steps: PlanStep[]) =>
  steps.find((step) => step.state === "now") ??
  steps.find((step) => step.state === "next");

/** The plan an answer last left. A subagent's plan is its own. */
function planOf(message: ChatMessage): PlanStep[] | undefined {
  let plan: PlanStep[] | undefined;
  for (const entry of message.trace ?? [])
    if (entry.kind === "activity" && !entry.activity.parentId)
      plan = entry.activity.plan ?? plan;
  return plan;
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
 * The plan a thread's main conversation is on. A finished one stays until the
 * next message. An unfinished one carries over to the next turn, until an
 * answer ends without having touched it: the agent has moved on.
 */
export function threadPlan(messages: ChatMessage[]): PlanStep[] | undefined {
  const main = mainConversation([...messages].sort(threadOrder));
  let askedSince = false;
  for (const message of main.reverse()) {
    const plan = planOf(message);
    if (plan) {
      const emptied = !plan.length;
      const putAway = askedSince && planIsOver(plan);
      return emptied || putAway ? undefined : plan;
    }
    if (isFinishedAnswer(message)) return undefined;
    if (message.role === "user") askedSince = true;
  }
  return undefined;
}
