import { Activity, Folder, FolderGit2, ListChecks } from "lucide-react";
import { currentStep, checklistProgress, type ChecklistStep } from "../../../shared/agent-checklist";
import { ComputerIcon, type CardComputer } from "./ComputerSection";

/** What the stripe says about the computer, for its label. */
function computerLabel(computer: CardComputer | undefined) {
  if (!computer) return "This computer";
  if (!computer.online) return `${computer.name} · offline`;
  return computer.name;
}

/** What the stripe says about the checklist, for its label. */
function progressLabel(checklist: ChecklistStep[]) {
  const { at, of } = checklistProgress(checklist);
  const step = currentStep(checklist);
  return step ? `${at} of ${of}: ${step.text}` : `${at} of ${of}`;
}

/** How far the checklist is, under its icon. */
function ProgressMark({ checklist }: { checklist: ChecklistStep[] }) {
  const { at, of } = checklistProgress(checklist);
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
 * the computer, the folder the thread works in, how far its agent's checklist is,
 * and how many processes run there. One button, which opens it.
 */
export function RunningStripe({
  computer,
  inWorktree,
  checklist,
  running,
  onOpen,
}: {
  computer?: CardComputer;
  /** The thread works in a worktree, not the project's checkout. */
  inWorktree: boolean;
  checklist?: ChecklistStep[];
  running: number;
  onOpen: () => void;
}) {
  const name = computerLabel(computer);
  const label = [
    name,
    checklist && progressLabel(checklist),
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
      {checklist && <ProgressMark checklist={checklist} />}
      {!!running && (
        <span className="running-stripe-mark">
          <Activity size={15} aria-hidden />
          <small>{running}</small>
        </span>
      )}
    </button>
  );
}
