import { execFileSync, spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdtemp, realpath, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, expect, it } from "vitest";
import { readOnlyGitEnv } from "./read-only-git";

let root: string;

beforeEach(async () => {
  root = await realpath(await mkdtemp(join(tmpdir(), "relay-read-only-git-")));
});

afterEach(async () => {
  await rm(root, { recursive: true, force: true });
});

/** A checkout with a bare repository committed in it that runs a command on `git status`. */
function checkoutWithTrap(marker: string) {
  const checkout = join(root, "checkout");
  execFileSync("git", ["init", "--quiet", checkout]);
  const git = (...args: string[]) =>
    execFileSync("git", args, { cwd: checkout, stdio: "ignore" });
  const trap = ["-C", "fixtures/evil.git", "config"];
  git("init", "--quiet", "--bare", "fixtures/evil.git");
  git(...trap, "core.bare", "false");
  git(...trap, "core.worktree", "..");
  git(...trap, "core.fsmonitor", `touch '${marker}'; false`);
  git("add", "-A");
  git("-c", "user.email=a@b", "-c", "user.name=a", "commit", "-qm", "trap");
  return checkout;
}

it("keeps git status in a committed bare repository from running its config", async () => {
  const marker = join(root, "ran");
  const checkout = checkoutWithTrap(marker);
  const status = (env: NodeJS.ProcessEnv) =>
    spawnSync("git", ["-C", "fixtures/evil.git", "status"], {
      cwd: checkout,
      env,
    });

  status(process.env);
  expect(existsSync(marker)).toBe(true);
  await rm(marker);

  status({ ...process.env, ...readOnlyGitEnv(process.env) });
  expect(existsSync(marker)).toBe(false);
});

it("names no variable Codex's default shell environment policy drops", () => {
  // Codex leaves out names matching *KEY*, *SECRET* and *TOKEN* unless told
  // not to, and git refuses every command when a counted key goes missing.
  const names = Object.keys(readOnlyGitEnv(process.env));
  expect(names.filter((name) => /KEY|SECRET|TOKEN/i.test(name))).toEqual([]);
});

it("passes its setting in the form git before 2.31 also reads", () => {
  // Older git splits GIT_CONFIG_PARAMETERS into shell-quoted 'key=value'
  // words and refuses every command on 'key'='value', which 2.31 added.
  // Debian 11 and Ubuntu 20.04 servers still ship such a git.
  const { GIT_CONFIG_PARAMETERS } = readOnlyGitEnv({});
  expect(GIT_CONFIG_PARAMETERS).toMatch(/^'[^']+=[^']+'$/);
});

it("keeps settings the environment already passes to git", () => {
  const checkout = checkoutWithTrap(join(root, "ran"));
  const base = {
    ...process.env,
    GIT_CONFIG_PARAMETERS: "'user.name'='Some One'",
  };
  const read = (key: string) =>
    execFileSync("git", ["config", key], {
      cwd: checkout,
      env: { ...base, ...readOnlyGitEnv(base) },
      encoding: "utf8",
    }).trim();
  expect(read("user.name")).toBe("Some One");
  expect(read("safe.bareRepository")).toBe("explicit");
});
