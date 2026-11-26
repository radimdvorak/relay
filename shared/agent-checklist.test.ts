import { describe, expect, it } from "vitest";
import { currentStep, keptSteps, checklistProgress, threadChecklist, type ChecklistStep } from "./agent-checklist";
import type { AgentActivity, ChatMessage } from "./projects";

const steps = (...states: ChecklistStep["state"][]): ChecklistStep[] =>
  states.map((state, index) => ({ text: `Step ${index + 1}`, state }));

let seq = 0;

const message = (extra: Partial<ChatMessage>): ChatMessage =>
  ({ id: `m${++seq}`, seq, role: "assistant", status: "complete", body: "", created: seq, provider: "codex", version: 1, ...extra }) as ChatMessage;

const asked = (extra: Partial<ChatMessage> = {}) => message({ role: "user", ...extra });

const answer = (checklist?: ChecklistStep[], extra: Partial<ChatMessage> = {}, call: Partial<AgentActivity> = {}) =>
  message({
    trace: checklist && [
      { kind: "activity", id: "checklist", activity: { id: "checklist", kind: "tool", label: "Updated progress", status: "complete", checklist, ...call } },
    ],
    ...extra,
  });

describe("threadChecklist", () => {
  it("shows the latest checklist while the answer runs and once it ends", () => {
    const old = answer(steps("done", "now"));
    const latest = answer(steps("now", "next"), { status: "streaming" });
    expect(threadChecklist([asked(), old, asked(), latest])).toEqual(steps("now", "next"));
    expect(threadChecklist([asked(), answer(steps("done", "done"))])).toEqual(steps("done", "done"));
  });

  it("drops a finished checklist at the next message, and keeps an unfinished one", () => {
    expect(threadChecklist([asked(), answer(steps("done", "failed")), asked()])).toBeUndefined();
    const open = steps("done", "next");
    expect(threadChecklist([asked(), answer(open), asked(), answer(undefined, { status: "streaming" })])).toEqual(open);
  });

  it("drops an unfinished checklist once an answer ends without touching it", () => {
    const open = answer(steps("done", "next"));
    expect(threadChecklist([asked(), open, asked(), answer()])).toBeUndefined();
    expect(threadChecklist([asked(), open, asked(), answer(undefined, { status: "cancelled" })])).toBeDefined();
    expect(threadChecklist([asked(), open, answer(undefined, { compaction: true })])).toBeDefined();
  });

  it("shows no checklist once the agent deleted every step", () => {
    expect(threadChecklist([asked(), answer(steps("now")), asked(), answer([])])).toBeUndefined();
  });

  it("leaves out a subagent's checklist and a side conversation's", () => {
    const own = steps("now");
    const started = answer(own);
    const under = answer(steps("now", "now"), { parentId: started.id });
    expect(threadChecklist([asked(), started, under])).toEqual(own);
    expect(threadChecklist([asked(), answer(steps("now"), {}, { parentId: "agent-call" })])).toBeUndefined();
  });

  it("leaves a /btw question and a reply whose parent isn't held out", () => {
    const finished = [asked(), answer(steps("done"))];
    const question = asked({ side: true });
    const sideAnswer = answer(undefined, { parentId: question.id });
    expect(threadChecklist([...finished, question, sideAnswer])).toEqual(steps("done"));
    const open = [asked(), answer(steps("now"))];
    const orphan = answer(undefined, { parentId: "not-held" });
    expect(threadChecklist([...open, orphan])).toEqual(steps("now"));
  });

  it("carries a checklist across the answers steers split a turn into", () => {
    const open = steps("done", "now");
    const turn = [asked(), answer(open), asked({ steered: true }), answer(), asked({ steered: true }), answer(undefined, { status: "streaming" })];
    expect(threadChecklist(turn)).toEqual(open);
    const finished = [asked(), answer(steps("done")), asked({ steered: true }), answer(undefined, { status: "streaming" })];
    expect(threadChecklist(finished)).toEqual(steps("done"));
    // A steer the turn ended before reading goes out as the next turn's request.
    const missed = [asked(), answer(open), asked({ steered: true, missed: true }), answer()];
    expect(threadChecklist(missed)).toBeUndefined();
  });

  it("reads messages in the thread's order, however they come", () => {
    const first = answer(steps("now"));
    const second = answer(steps("done"));
    expect(threadChecklist([second, first])).toEqual(steps("done"));
  });
});

it("counts the step being worked on", () => {
  expect(checklistProgress(steps("done", "failed", "now", "next"))).toEqual({ at: 3, of: 4 });
  expect(checklistProgress(steps("done", "done"))).toEqual({ at: 2, of: 2 });
  expect(checklistProgress(steps("next", "next"))).toEqual({ at: 0, of: 2 });
  expect(currentStep(steps("done", "next", "now"))?.text).toBe("Step 3");
});

it("keeps only steps that say something", () => {
  expect(keptSteps([{ text: "  ", state: "next" }])).toBeUndefined();
  expect(keptSteps([{ text: " Build ", state: "now" }])).toEqual([{ text: "Build", state: "now" }]);
});
