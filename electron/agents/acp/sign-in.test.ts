import { mkdtemp, readFile, realpath } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { afterEach, expect, it } from "vitest";
import type { AcpProfile } from "./profiles";
import { disposeAcp } from "./sessions";
import { renewAcpSignIn } from "./sign-in";

let profile: AcpProfile | undefined;

afterEach(() => {
  if (profile) disposeAcp(profile);
});

async function antigravity(scenario: object) {
  const root = await realpath(await mkdtemp(join(tmpdir(), "relay-renew-")));
  const log = join(root, "sent.jsonl");
  profile = {
    provider: "antigravity",
    name: "Antigravity",
    home: root,
    command: async () => ({
      command: process.execPath,
      args: [resolve("tests/fixtures/acp-agent.cjs")],
      env: {
        RELAY_ACP_LOG: log,
        RELAY_ACP_SCENARIO: JSON.stringify({
          captureEnvKeys: ["SSH_CONNECTION", "DISPLAY"],
          signIn: "oauth-personal",
          ...scenario,
        }),
      },
    }),
    wishes: () => [],
    install: "",
  };
  const sent = async () =>
    (await readFile(log, "utf8"))
      .split("\n")
      .filter(Boolean)
      .map((line) => JSON.parse(line));
  return { profile, sent };
}

it("renews a sign-in without opening a session, and with no browser to open", async () => {
  const { profile, sent } = await antigravity({});
  await renewAcpSignIn(profile);
  const lines = await sent();
  expect(lines.map((line) => line.method).filter(Boolean)).toEqual([
    "initialize",
    "authenticate",
  ]);
  const { environment } = lines.find((line) => line.environment);
  expect(environment.SSH_CONNECTION).toBeTruthy();
  expect(environment.DISPLAY).toBe("");
});

it("fails when Google no longer takes the sign-in", async () => {
  const { profile } = await antigravity({ refuse: "No browser is available" });
  await expect(renewAcpSignIn(profile)).rejects.toThrow("No browser");
});
