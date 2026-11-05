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
