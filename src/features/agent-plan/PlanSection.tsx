import { useEffect, useRef } from "react";
import { ChevronRight } from "lucide-react";
import { currentStep, planProgress, type PlanStep } from "../../../shared/agent-plan";
import { useStoredFlag } from "../../lib/useStoredFlag";
import { PlanList } from "./PlanList";

/** Scrolls a long plan to the step the agent is on, as it gets there. */
function useCurrentInView(at: number) {
  const list = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const view = list.current;
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

function PlanSteps({ steps }: { steps: PlanStep[] }) {
  const current = currentStep(steps);
  const list = useCurrentInView(current ? steps.indexOf(current) : 0);
  return (
    <div className="plan-section-steps" ref={list}>
      <PlanList steps={steps} />
    </div>
  );
}

/** The plan the thread's agent is on, in the thread's card; the fold is the same for every thread. */
export function PlanSection({ steps }: { steps: PlanStep[] }) {
  const [folded, setFolded] = useStoredFlag("relay-plan-folded");
  const { at, of } = planProgress(steps);
  return (
    <section className="running-tasks-section plan-section">
      <button
        type="button"
        className="running-tasks-label"
        aria-expanded={!folded}
        onClick={() => setFolded(!folded)}
      >
        <ChevronRight size={12} aria-hidden />
        Plan
        <span className="plan-section-count">
          {at} of {of}
        </span>
      </button>
      {!folded && <PlanSteps steps={steps} />}
    </section>
  );
}
