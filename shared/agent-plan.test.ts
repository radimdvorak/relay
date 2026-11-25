import { describe, expect, it } from "vitest";
import { currentStep, keptSteps, planProgress, threadPlan, type PlanStep } from "./agent-plan";
import type { AgentActivity, ChatMessage } from "./projects";

const steps = (...states: PlanStep["state"][]): PlanStep[] =>
  states.map((state, index) => ({ text: `Step ${index + 1}`, state }));

let seq = 0;

const message = (extra: Partial<ChatMessage>): ChatMessage =>
  ({ id: `m${++seq}`, seq, role: "assistant", status: "complete", body: "", created: seq, provider: "codex", version: 1, ...extra }) as ChatMessage;

const asked = (extra: Partial<ChatMessage> = {}) => message({ role: "user", ...extra });

const answer = (plan?: PlanStep[], extra: Partial<ChatMessage> = {}, call: Partial<AgentActivity> = {}) =>
  message({
    trace: plan && [
      { kind: "activity", id: "plan", activity: { id: "plan", kind: "tool", label: "Updated the plan", status: "complete", plan, ...call } },
    ],
    ...extra,
  });

describe("threadPlan", () => {
  it("shows the latest plan while the answer runs and once it ends", () => {
    const old = answer(steps("done", "now"));
    const latest = answer(steps("now", "next"), { status: "streaming" });
    expect(threadPlan([asked(), old, asked(), latest])).toEqual(steps("now", "next"));
    expect(threadPlan([asked(), answer(steps("done", "done"))])).toEqual(steps("done", "done"));
  });

  it("drops a finished plan at the next message, and keeps an unfinished one", () => {
    expect(threadPlan([asked(), answer(steps("done", "failed")), asked()])).toBeUndefined();
    const open = steps("done", "next");
    expect(threadPlan([asked(), answer(open), asked(), answer(undefined, { status: "streaming" })])).toEqual(open);
  });

  it("drops an unfinished plan once an answer ends without touching it", () => {
    const open = answer(steps("done", "next"));
    expect(threadPlan([asked(), open, asked(), answer()])).toBeUndefined();
    expect(threadPlan([asked(), open, asked(), answer(undefined, { status: "cancelled" })])).toBeDefined();
    expect(threadPlan([asked(), open, answer(undefined, { compaction: true })])).toBeDefined();
  });

  it("shows no plan once the agent deleted every step", () => {
    expect(threadPlan([asked(), answer(steps("now")), asked(), answer([])])).toBeUndefined();
  });

  it("leaves out a subagent's plan and a side conversation's", () => {
    const own = steps("now");
    const started = answer(own);
    const under = answer(steps("now", "now"), { parentId: started.id });
    expect(threadPlan([asked(), started, under])).toEqual(own);
    expect(threadPlan([asked(), answer(steps("now"), {}, { parentId: "agent-call" })])).toBeUndefined();
  });

  it("reads messages in the thread's order, however they come", () => {
    const first = answer(steps("now"));
    const second = answer(steps("done"));
    expect(threadPlan([second, first])).toEqual(steps("done"));
  });
});

it("counts the step being worked on", () => {
  expect(planProgress(steps("done", "failed", "now", "next"))).toEqual({ at: 3, of: 4 });
  expect(planProgress(steps("done", "done"))).toEqual({ at: 2, of: 2 });
  expect(planProgress(steps("next", "next"))).toEqual({ at: 0, of: 2 });
  expect(currentStep(steps("done", "next", "now"))?.text).toBe("Step 3");
});

it("keeps only steps that say something", () => {
  expect(keptSteps([{ text: "  ", state: "next" }])).toBeUndefined();
  expect(keptSteps([{ text: " Build ", state: "now" }])).toEqual([{ text: "Build", state: "now" }]);
});
