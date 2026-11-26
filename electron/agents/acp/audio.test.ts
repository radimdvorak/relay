import { mkdtemp, readFile, realpath, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, expect, it, vi } from "vitest";
import type { AgentOptions } from "../types";
import type { AcpProfile } from "./profiles";
import { runAcp } from "./run";
import { disposeAcp } from "./sessions";

const fixture = join(__dirname, "../../../tests/fixtures/acp-agent.cjs");
const wavHeader = Buffer.from("RIFF\x24\x00\x00\x00WAVEfmt ");

type Sent = { id?: number; method?: string; params?: any; result?: any };

const profiles: AcpProfile[] = [];

afterEach(() => {
  for (const profile of profiles.splice(0)) disposeAcp(profile);
});

async function fixtureAgent(
  scenario: Record<string, unknown>,
  profile: Partial<AcpProfile> = {},
) {
  const root = await realpath(
    await mkdtemp(join(tmpdir(), "relay-acp-audio-")),
  );
  const log = join(root, "sent.jsonl");
  const acp: AcpProfile = {
    provider: "antigravity",
    name: "Antigravity",
    home: root,
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
    ...profile,
  };
  profiles.push(acp);
  const sent = async (): Promise<Sent[]> =>
    (await readFile(log, "utf8").catch(() => ""))
      .split("\n")
      .filter(Boolean)
      .map((line) => JSON.parse(line));
  return { acp, root, sent };
}

function options(cwd: string, extra: Partial<AgentOptions> = {}): AgentOptions {
  return {
    cwd,
    prompt: "Transcribe this clip.",
    choice: { model: "", reasoningEffort: "", fast: false },
    runtimeMode: "approval-required",
    interactionMode: "default",
    job: { kind: "prompt" },
    signal: new AbortController().signal,
    onText: () => {},
    onActivity: () => {},
    ...extra,
  };
}

it("sends audio attachments as ACP audio content blocks when the agent advertises audio capability", async () => {
  const { acp, root, sent } = await fixtureAgent({
    audio: true,
    turn: [
      {
        update: {
          sessionUpdate: "agent_message_chunk",
          content: { type: "text", text: "Heard it." },
        },
      },
    ],
  });
  const clipPath = join(root, "clip.wav");
  await writeFile(clipPath, wavHeader);

  const answer = await runAcp(
    acp,
    options(root, {
      audio: [{ path: clipPath, mimeType: "audio/wav" }],
    }),
  );

  expect(answer).toBe("Heard it.");
  const prompt = (await sent()).find((m) => m.method === "session/prompt")
    ?.params.prompt;
  expect(prompt).toEqual([
    { type: "text", text: "Transcribe this clip." },
    {
      type: "audio",
      mimeType: "audio/wav",
      data: wavHeader.toString("base64"),
    },
  ]);
});

it("refuses audio attachments cleanly before prompting when the ACP agent lacks audio capability", async () => {
  const { acp, root, sent } = await fixtureAgent({ audio: false });
  const clipPath = join(root, "clip.wav");
  await writeFile(clipPath, wavHeader);

  await expect(
    runAcp(
      acp,
      options(root, {
        audio: [{ path: clipPath, mimeType: "audio/wav" }],
      }),
    ),
  ).rejects.toThrow("Antigravity can't listen to audio.");

  expect((await sent()).map((m) => m.method)).not.toContain("session/prompt");
});

it("points Antigravity at attached audio paths via read_file and auto-approves reading only those clips", async () => {
  const clipDir = await realpath(await mkdtemp(join(tmpdir(), "relay-clip-")));
  const clipPath = join(clipDir, "voice.wav");
  const secretPath = join(clipDir, "other.txt");
  await writeFile(clipPath, wavHeader);
  const allowOrDeny = [
    { optionId: "allow", name: "Allow", kind: "allow_once" },
    { optionId: "deny", name: "Deny", kind: "reject_once" },
  ];
  const { acp, root, sent } = await fixtureAgent(
    {
      audio: false,
      turn: [
        {
          ask: {
            toolCall: {
              toolCallId: "read-clip",
              name: "read_file",
              kind: "read",
              title: "Read voice.wav",
              rawInput: { Path: clipPath },
              locations: [{ path: clipPath }],
            },
            options: allowOrDeny,
          },
        },
        {
          ask: {
            toolCall: {
              toolCallId: "read-other",
              name: "read_file",
              kind: "read",
              title: "Read other.txt",
              rawInput: { Path: secretPath },
              locations: [{ path: secretPath }],
            },
            options: allowOrDeny,
          },
        },
        {
          ask: {
            toolCall: {
              toolCallId: "edit-clip",
              name: "write_file",
              kind: "edit",
              title: "Edit voice.wav",
              rawInput: { Path: clipPath },
              locations: [{ path: clipPath }],
            },
            options: allowOrDeny,
          },
        },
        {
          update: {
            sessionUpdate: "agent_message_chunk",
            content: { type: "text", text: "Listened via read_file." },
          },
        },
      ],
    },
    { readsAudioFiles: true },
  );
  const asked: string[] = [];
  const onRequest = vi.fn(async (request: { kind: string; title?: string }) => {
    if (request.kind === "approval" && request.title) asked.push(request.title);
    return { kind: "approval" as const, decision: "decline" as const };
  });

  const answer = await runAcp(
    acp,
    options(root, {
      runtimeMode: "approval-required",
      audio: [{ path: clipPath, mimeType: "audio/wav" }],
      onRequest,
    }),
  );

  expect(answer).toBe("Listened via read_file.");
  const prompt = (await sent()).find((m) => m.method === "session/prompt")
    ?.params.prompt;
  expect(prompt).toEqual([
    { type: "text", text: "Transcribe this clip." },
    {
      type: "text",
      text: expect.stringContaining(`read_file`),
      annotations: { audience: ["assistant"] },
    },
  ]);
  expect(prompt[1].text).toContain(`${clipPath} (audio/wav)`);
  expect(asked).toEqual(["Read other.txt", "Edit voice.wav"]);
  const replies = (await sent())
    .filter((m) => m.method === undefined && m.result?.outcome)
    .map((m) => m.result.outcome.optionId);
  expect(replies).toEqual(["allow", "deny", "deny"]);
});
