import { Check, X } from "lucide-react";
import type { PlanStep } from "../../../shared/agent-plan";
import "./agent-plan.css";

/** Ticked, half-filled while the agent is on it, crossed when it failed, or an empty ring. */
function StepMark({ state }: { state: PlanStep["state"] }) {
  if (state === "done") return <Check size={14} aria-hidden />;
  if (state === "failed") return <X size={14} aria-hidden />;
  return (
    <svg width={14} height={14} viewBox="0 0 14 14" aria-hidden>
      <circle cx="7" cy="7" r="5.25" />
      {state === "now" && <circle className="core" cx="7" cy="7" r="2.5" />}
    </svg>
  );
}

const spoken: Record<PlanStep["state"], string> = {
  next: "Not started",
  now: "Working on it",
  done: "Done",
  failed: "Failed",
};

/** A plan's steps and where each stands. Still: the running call is what moves. */
export function PlanList({ steps }: { steps: PlanStep[] }) {
  return (
    <ol className="plan-list">
      {steps.map((step, index) => (
        <li key={index} data-state={step.state}>
          <StepMark state={step.state} />
          <span>
            {step.text}
            <span className="sr-only">, {spoken[step.state]}</span>
          </span>
        </li>
      ))}
    </ol>
  );
}
