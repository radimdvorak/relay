import { mkdtemp, readFile, realpath, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, expect, it } from "vitest";
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

async function fixtureAgent(scenario: Record<string, unknown>) {
  const root = await realpath(await mkdtemp(join(tmpdir(), "relay-acp-audio-")));
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
  };
  profiles.push(acp);
  const sent = async (): Promise<Sent[]> =>
    (await readFile(log, "utf8").catch(() => ""))
      .split("\n")
      .filter(Boolean)
      .map((line) => JSON.parse(line));
  return { acp, root, sent };
}

function options(
  cwd: string,
  extra: Partial<AgentOptions> = {},
): AgentOptions {
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
