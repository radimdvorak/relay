import { expect, it } from "vitest";
import type { AsyncAgentQuestions, ChatMessage } from "../../shared/projects";
import { asksYou } from "./chat-summaries";

/** An answer whose agent asked for a secret, which ended as `answers` says. */
const asked = (rest: Partial<AsyncAgentQuestions>): ChatMessage[] => [
  {
    id: "answer",
    role: "assistant",
    body: "",
    provider: "claude",
    status: "complete",
    created: 1,
    version: 1,
    questions: [
      {
        id: "item",
        secret: { name: "npm publish token" },
        questions: [{ id: "secret", question: "npm publish token" }],
        ...rest,
      },
    ],
  },
];

// The sidebar's dot, Activity, the phones' lists and their notifications all
// read this one flag, so a secret request waits wherever a question does.
it("waits on you while a secret request is open, and no longer once it ended", () => {
  expect(asksYou(asked({}))).toBe(true);
  expect(asksYou(asked({ answers: { secret: ["Saved"] } }))).toBe(false);
  expect(asksYou(asked({ answers: {} }))).toBe(false);
  // Handed over by Always: there was never a card to wait on.
  expect(asksYou(asked({ answers: { secret: ["Remembered"] } }))).toBe(false);
});
