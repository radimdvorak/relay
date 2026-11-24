import { mkdir, mkdtemp, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { configureAntigravity } from "../antigravity";
import { acpProfiles, antigravityProfile } from "./profiles";

afterEach(() => vi.unstubAllEnvs());

describe("Antigravity's sign-in on disk", () => {
  const antigravity = () => acpProfiles.antigravity.account!();
  let own: string;

  beforeEach(async () => {
    const home = await mkdtemp(join(tmpdir(), "relay-antigravity-"));
    own = join(home, "antigravity-acp");
    await mkdir(own);
    vi.stubEnv("GEMINI_HOME", home);
    // Off the keychain, so the token is a file this test can write.
    vi.stubEnv("AGY_ACP_FORCE_FILE_STORAGE", "1");
  });
  const save = (name: string, value: unknown) =>
    writeFile(join(own, name), JSON.stringify(value));

  it("is signed out until the CLI has saved its token, whatever the old server left", async () => {
    expect(await antigravity()).toEqual({ signedIn: false });
    await save("settings.json", { auth: { type: "oauth-personal" } });
    await save("acp_token.json", { refresh_token: "r" });
    expect(await antigravity()).toEqual({ signedIn: false });
    await save("antigravity-oauth-token", { token: { access_token: "a" } });
    expect(await antigravity()).toEqual({ signedIn: true });
  });

  it("starts the CLI with its folders in the profile, updating only through Relay", async () => {
    vi.stubEnv("AGY_ACP_FORCE_FILE_STORAGE", "");
    const home = join(own, "..");
    await mkdir(join(home, "cli", "1.5.16"), { recursive: true });
    await writeFile(join(home, "cli", "1.5.16", "antigravity"), "");
    await writeFile(
      join(home, "cli", "current.json"),
      JSON.stringify({ version: "1.5.16", cmd: "antigravity", args: [] }),
    );
    configureAntigravity({ root: join(home, "cli"), fetch });

    const usual = await acpProfiles.antigravity.command();
    expect(usual.args).toEqual([
      "acp",
      "--gemini-dir",
      home,
      "--app-data-dir",
      "antigravity-acp",
    ]);
    expect(usual.env).toEqual({ AGY_CLI_DISABLE_AUTO_UPDATE: "1" });

    // Another account's sign-in must stay out of the one shared Keychain item.
    const other = antigravityProfile(
      { GEMINI_HOME: "/accounts/b", AGY_ACP_FORCE_FILE_STORAGE: "1" },
      "b",
    );
    const { args, env } = await other.command();
    expect(args).toContain("/accounts/b");
    expect(env?.SSH_CONNECTION).toBeTruthy();
  });

  it("leaves other sign-ins to Antigravity", async () => {
    await save("settings.json", { auth: { type: "agent-platform" } });
    expect(await antigravity()).toBeUndefined();
  });
});
