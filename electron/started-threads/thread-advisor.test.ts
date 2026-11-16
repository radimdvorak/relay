import { expect, it, vi } from "vitest";
import type { ThreadAdvisor as Picked } from "../../shared/advisor";
import type { ChatMessage, ProjectChat } from "../../shared/projects";
import { ADVISOR_CALLS_PER_TURN, resultText } from "../relay-mcp";
import { ThreadAdvisor } from "./thread-advisor";

vi.mock("../agents", () => ({ agentRuntime: () => ({}) }));

const message = (
  id: string,
  role: ChatMessage["role"],
  body: string,
  extra: Partial<ChatMessage> = {},
): ChatMessage =>
  ({
    id,
    role,
    body,
    provider: "codex",
    status: "complete",
    created: 1,
    version: 1,
    ...extra,
  }) as ChatMessage;

const thread = (advisor: Picked | undefined, turn = "m1"): ProjectChat =>
  ({
    id: "t1",
    projectId: "p1",
    messages: [
      message("m1", "user", "@codex Why does the cache miss?"),
      message("m2", "assistant", "", {
        status: "streaming",
        trace: [
          {
            kind: "activity",
            id: "a1",
            activity: {
              id: "a1",
              kind: "command",
              label: "npm test",
              status: "complete",
            },
          },
        ],
      }),
      message("s1", "user", "@codex a side question", { side: true }),
    ],
    lastInput: {
      id: turn,
      body: "@codex Why does the cache miss?",
      provider: "codex",
      choice: { model: "gpt-6-luna", reasoningEffort: "", fast: false },
      runtimeMode: "auto",
      interactionMode: "default",
      ...(advisor ? { advisor } : {}),
    },
  }) as unknown as ProjectChat;

const fable = { provider: "claude" as const, model: "fable" };
const signal = new AbortController().signal;

it("asks the thread's advisor with the conversation so far and the agent's question", async () => {
  const run = vi.fn(async () => "Check the key's TTL first.");
  const advisor = new ThreadAdvisor(run);
  const answer = await advisor.ask(
    thread(fable),
    { question: "Is it the TTL?" },
    signal,
  );
  expect(resultText(answer)).toBe("Check the key's TTL first.");
  const [asked, prompt] = run.mock.calls[0] as unknown as [unknown, string];
  expect(asked).toEqual(fable);
  expect(prompt).toContain("### User\n@codex Why does the cache miss?");
  // What the agent did this turn, so far.
  expect(prompt).toContain("### Agent\n- command: npm test");
  expect(prompt).not.toContain("a side question");
  expect(prompt).toMatch(/## The agent asks you\n\nIs it the TTL\?$/);
});

it("reads the reply conversation the agent works in, after what it replies to", async () => {
  const run = vi.fn(async () => "Advice.");
  const replying = thread(fable);
  replying.messages.push(
    message("m3", "user", "@codex And the eviction?", { parentId: "m2" }),
    message("m4", "assistant", "Looking at the LRU.", { parentId: "m3" }),
    message("m5", "user", "@codex Something else", { created: 2 }),
  );
  replying.lastInput = { ...replying.lastInput!, id: "m3", parentId: "m2" };
  await new ThreadAdvisor(run).ask(replying, { question: "?" }, signal);
  const prompt = (run.mock.calls[0] as unknown as [unknown, string])[1];
  expect(prompt).toContain("### User\n@codex Why does the cache miss?");
  expect(prompt).toContain(
    "### User\n@codex And the eviction?\n\n### Agent\nLooking at the LRU.",
  );
  expect(prompt).not.toContain("Something else");
});

it("refuses a thread without an advisor", async () => {
  const run = vi.fn(async () => "");
  const advisor = new ThreadAdvisor(run);
  for (const picked of [undefined, "off" as const]) {
    const answer = await advisor.ask(thread(picked), { question: "?" }, signal);
    expect(answer.isError).toBe(true);
  }
  expect(run).not.toHaveBeenCalled();
});

it("answers a few times a turn, and again in the next one", async () => {
  const run = vi.fn(async () => "Advice.");
  const advisor = new ThreadAdvisor(run);
  const ask = (turn: string) =>
    advisor.ask(thread(fable, turn), { question: "?" }, signal);
  for (let i = 0; i < ADVISOR_CALLS_PER_TURN; i++)
    expect((await ask("m1")).isError).toBeFalsy();
  expect((await ask("m1")).isError).toBe(true);
  expect((await ask("m3")).isError).toBeFalsy();
  expect(run).toHaveBeenCalledTimes(ADVISOR_CALLS_PER_TURN + 1);
});
