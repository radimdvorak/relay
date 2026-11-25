import { mkdtemp, readFile, realpath } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, expect, it } from "vitest";
import type { AgentOptions } from "../types";
import type { AcpProfile } from "./profiles";
import { ReadOnlyRefusals } from "./read-only";
import { runAcp } from "./run";
import { disposeAcp } from "./sessions";

const fixture = join(__dirname, "../../../tests/fixtures/acp-agent.cjs");
const profiles: AcpProfile[] = [];

afterEach(() => {
  for (const profile of profiles.splice(0)) disposeAcp(profile);
});

/** Runs a read-only turn against the fixture agent; `sent` is every message Relay wrote to it. */
async function readOnlyTurn(
  scenario: Record<string, unknown>,
  more: Partial<AgentOptions> = {},
  provider: AcpProfile["provider"] = "antigravity",
) {
  const cwd = await realpath(await mkdtemp(join(tmpdir(), "relay-acp-ro-")));
  const log = join(cwd, "sent.jsonl");
  const profile: AcpProfile = {
    provider,
    name: "Agent",
    home: cwd,
    command: async () => ({
      command: process.execPath,
      args: [fixture],
      env: {
        RELAY_ACP_SCENARIO: JSON.stringify(scenario),
        RELAY_ACP_LOG: log,
      },
    }),
    wishes: () => [],
    install: "",
  };
  profiles.push(profile);
  const outcome = await runAcp(profile, {
    job: { kind: "prompt" },
    cwd,
    prompt: "Review it",
    choice: { model: "", reasoningEffort: "", fast: false },
    runtimeMode: "approval-required",
    interactionMode: "default",
    readOnly: true,
    signal: new AbortController().signal,
    onText() {},
    ...more,
  }).then(
    (answer) => ({ answer, error: undefined }),
    (error: Error) => ({ answer: undefined, error: error.message }),
  );
  const sent = (await readFile(log, "utf8"))
    .split("\n")
    .filter(Boolean)
    .map((line) => JSON.parse(line));
  return {
    ...outcome,
    prompts: sent.filter((m) => m.method === "session/prompt").length,
    answers: sent
      .filter((m) => m.method === undefined && m.result?.outcome)
      .map((m) => m.result.outcome.optionId),
  };
}

const options = [
  { optionId: "always", name: "Always", kind: "allow_always" },
  { optionId: "yes", name: "Allow", kind: "allow_once" },
  { optionId: "no", name: "Reject", kind: "reject_once" },
];

const asks = (toolCall: Record<string, unknown>, offered = options) => ({
  ask: {
    toolCall: { toolCallId: "t1", title: "Call", ...toolCall },
    options: offered,
  },
});

const runs = (command: string, offered = options) =>
  asks({ kind: "execute", rawInput: { CommandLine: command } }, offered);

const says = (text: string) => ({
  update: {
    sessionUpdate: "agent_message_chunk",
    content: { type: "text", text },
  },
});

const callDone = (toolCallId: string) => ({
  update: {
    sessionUpdate: "tool_call",
    toolCallId,
    title: "Read",
    kind: "read",
    status: "completed",
  },
});

const carryOn = "Relay turned that call down";

const onPosix = it.skipIf(process.platform === "win32");

onPosix(
  "lets a read-only turn run commands that only read, and turns the rest down",
  async () => {
    const { answers, prompts } = await readOnlyTurn({
      turn: [
        runs(
          'git log --oneline a1..b2 && echo "---STAT---" && git diff --stat a1 b2 && git status --short',
        ),
        runs("git show b2:src/app.ts"),
        runs("git diff a1 b2 > /tmp/out.diff"),
        runs("git push"),
        runs("rm -rf ."),
        asks({ kind: "execute" }),
        says("No problems found."),
      ],
    });
    expect(answers).toEqual(["yes", "yes", "no", "no", "no", "no"]);
    // It answered, so a refusal on the way is nothing to carry on from.
    expect(prompts).toBe(1);
  },
);

it("lets a read-only turn look, but not change, reach out or call an MCP server", async () => {
  const { answers } = await readOnlyTurn({
    turn: [
      asks({ kind: "read" }),
      asks({ kind: "search" }),
      asks({ kind: "edit" }),
      asks({ kind: "fetch" }),
      asks({ kind: "other" }),
      asks({ kind: "read", name: "mcp_db_query" }),
      says("ok"),
    ],
  });
  expect(answers).toEqual(["yes", "yes", "no", "no", "no", "no"]);
});

it("never takes an Always, which would outlive the read-only turn", async () => {
  const { answers } = await readOnlyTurn({
    turn: [
      runs(
        "git status",
        options.filter((o) => o.kind !== "allow_once"),
      ),
      says("ok"),
    ],
  });
  expect(answers).toEqual(["no"]);
});

for (const provider of ["antigravity", "opencode", "amp"] as const)
  for (const end of ["end_turn", "cancelled"])
    it(`has ${provider} carry on after a refused call ended its turn as ${end}`, async () => {
      const { answer, prompts } = await readOnlyTurn(
        {
          turn: [runs("npm test"), { end }],
          turnByPrefix: { [carryOn]: [says("P1 a bug")] },
        },
        {},
        provider,
      );
      expect(answer).toBe("P1 a bug");
      expect(prompts).toBe(2);
    });

it("gives up after two carry-ons that got nowhere, and says why", async () => {
  const { error, prompts } = await readOnlyTurn({ turn: [runs("npm test")] });
  expect(prompts).toBe(3);
  expect(error).toContain("read-only thread doesn't allow");
});

it("keeps carrying on while calls go through, up to ten times", () => {
  const refusals = new ReadOnlyRefusals();
  let completed = 0;
  let told = 0;
  while (refusals.carryOn(++completed)) told++;
  expect(told).toBe(10);
});

it("leaves a turn alone that ended without an answer but was refused nothing", async () => {
  const { answer, prompts } = await readOnlyTurn({ turn: [callDone("r1")] });
  expect(answer).toBe("");
  expect(prompts).toBe(1);
});

it("leaves the user's own refusal as the end of the turn", async () => {
  const { prompts } = await readOnlyTurn(
    { turn: [runs("npm test")] },
    {
      readOnly: false,
      onRequest: async () => ({ kind: "approval", decision: "decline" }),
    },
  );
  expect(prompts).toBe(1);
});

it("stays stopped when the user stops it while a call is refused", async () => {
  const stop = new AbortController();
  const { error, prompts } = await readOnlyTurn(
    { turn: [runs("npm test"), says("…"), { waitCancel: true }] },
    { signal: stop.signal, onText: (text) => text && stop.abort() },
  );
  expect(error).toBe("Cancelled by you.");
  expect(prompts).toBe(1);
});
