import { useEffect, useRef } from "react";
import { ChevronRight } from "lucide-react";
import { currentStep, checklistProgress, type ChecklistStep } from "../../../shared/agent-checklist";
import { useStoredFlag } from "../../lib/useStoredFlag";
import { Checklist } from "./Checklist";

/**
 * Scrolls a long checklist to the step the agent is on, as it gets there.
 * Once every step is over there is none, and the list stays where it is.
 */
function useCurrentInView(at: number | undefined) {
  const list = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const view = list.current;
    if (at === undefined) return;
    const step = view?.querySelectorAll("li")[at];
    if (!view || !step) return;
    const top = step.offsetTop - view.offsetTop;
    const above = top < view.scrollTop;
    const below = top + step.offsetHeight > view.scrollTop + view.clientHeight;
    if (above || below)
      view.scrollTop = top - (view.clientHeight - step.offsetHeight) / 2;
  }, [at]);
  return list;
}

function ScrolledSteps({ steps }: { steps: ChecklistStep[] }) {
  const current = currentStep(steps);
  const list = useCurrentInView(current && steps.indexOf(current));
  return (
    <div className="progress-section-steps" ref={list}>
      <Checklist steps={steps} />
    </div>
  );
}

/** The checklist the thread's agent is on, in the thread's card; the fold is the same for every thread. */
export function ProgressSection({ steps }: { steps: ChecklistStep[] }) {
  const [folded, setFolded] = useStoredFlag("relay-progress-folded");
  const { at, of } = checklistProgress(steps);
  return (
    <section className="running-tasks-section progress-section">
      <button
        type="button"
        className="running-tasks-label"
        aria-expanded={!folded}
        onClick={() => setFolded(!folded)}
      >
        <ChevronRight size={12} aria-hidden />
        Progress
        <span className="progress-section-count">
          {at} of {of}
        </span>
      </button>
      {!folded && <ScrolledSteps steps={steps} />}
    </section>
  );
}
