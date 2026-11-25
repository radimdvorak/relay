import { Activity, Folder, FolderGit2, ListChecks } from "lucide-react";
import { currentStep, planProgress, type PlanStep } from "../../../shared/agent-plan";
import { ComputerIcon, type CardComputer } from "./ComputerSection";

/** What the stripe says about the computer, for its label. */
function computerLabel(computer: CardComputer | undefined) {
  if (!computer) return "This computer";
  if (!computer.online) return `${computer.name} · offline`;
  return computer.name;
}

/** What the stripe says about the plan, for its label. */
function planLabel(plan: PlanStep[]) {
  const { at, of } = planProgress(plan);
  const step = currentStep(plan);
  return step ? `${at} of ${of}: ${step.text}` : `${at} of ${of}`;
}

/** How far the plan is, under its icon. */
function PlanMark({ plan }: { plan: PlanStep[] }) {
  const { at, of } = planProgress(plan);
  return (
    <span className="running-stripe-mark">
      <ListChecks size={15} aria-hidden />
      <small>
        {at}/{of}
      </small>
    </span>
  );
}

/**
 * The card folded into the thread's right gutter, where there's never text:
 * the computer, the folder the thread works in, how far its agent's plan is,
 * and how many processes run there. One button, which opens it.
 */
export function RunningStripe({
  computer,
  inWorktree,
  plan,
  running,
  onOpen,
}: {
  computer?: CardComputer;
  /** The thread works in a worktree, not the project's checkout. */
  inWorktree: boolean;
  plan?: PlanStep[];
  running: number;
  onOpen: () => void;
}) {
  const name = computerLabel(computer);
  const label = [
    name,
    plan && planLabel(plan),
    running > 0 && `${running} running`,
  ]
    .filter(Boolean)
    .join(" · ");
  return (
    <button
      type="button"
      className="running-stripe"
      aria-expanded={false}
      aria-label={label}
      title={label}
      onClick={onOpen}
    >
      <span className="running-stripe-mark">
        <ComputerIcon computer={computer} />
      </span>
      <span className="running-stripe-mark">
        {inWorktree ? (
          <FolderGit2 size={15} aria-hidden />
        ) : (
          <Folder size={15} aria-hidden />
        )}
      </span>
      {plan && <PlanMark plan={plan} />}
      {!!running && (
        <span className="running-stripe-mark">
          <Activity size={15} aria-hidden />
          <small>{running}</small>
        </span>
      )}
    </button>
  );
}
