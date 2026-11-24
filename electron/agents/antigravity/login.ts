// Signs a separate Antigravity profile in with `agy acp --login`. Its token
// goes to a file of that profile's; the browser sign-in over ACP would put it
// in the one Keychain item every profile shares.
import type { ChildProcess } from "node:child_process";
import { spawnExecutable } from "../../platform/executables";
import { terminate } from "../../platform/terminate";
import { antigravityHome } from "./account";
import { antigravityCommand } from "./command";
import { ensureAntigravity } from "./install";

/** Long enough to find the right browser window and pick an organisation. */
const TIMEOUT = 10 * 60_000;

export interface CodeLogin {
  done: Promise<void>;
  cancel(): void;
  /** Hands the CLI the code Google's page showed after signing in. */
  enterCode(code: string): void;
}

/** The Google link the CLI prints, once it has. */
const googleLink = (output: string) =>
  /https:\/\/accounts\.google\.com\/\S+/.exec(output)?.[0];

export async function startCodeLogin(
  env: Record<string, string>,
  openUrl: (url: string) => Promise<void>,
): Promise<CodeLogin> {
  const line = antigravityCommand(
    await ensureAntigravity(),
    antigravityHome(env),
    true,
    ["--login"],
  );
  const child: ChildProcess = spawnExecutable(line.command, line.args, {
    env: { ...env, ...line.env },
    stdio: ["pipe", "pipe", "pipe"],
    windowsHide: true,
  });
  let output = "",
    opened = false,
    cancelled = false;
  const collect = (chunk: Buffer) => {
    output = (output + chunk.toString()).slice(-8000);
    const link = !opened && googleLink(output);
    if (!link) return;
    opened = true;
    openUrl(link).catch(() => {});
  };
  child.stdout?.on("data", collect);
  child.stderr?.on("data", collect);
  // A code entered as the CLI exits has nowhere to go; its exit says why.
  child.stdin?.on("error", () => {});
  const timer = setTimeout(() => terminate(child), TIMEOUT);
  const done = new Promise<void>((resolve, reject) => {
    child.once("error", (e) => {
      clearTimeout(timer);
      reject(e);
    });
    child.once("exit", (code) => {
      clearTimeout(timer);
      if (cancelled) reject(new Error("Sign-in cancelled."));
      else if (code === 0) resolve();
      else reject(new Error(lastWords(output) || "The sign-in didn't finish."));
    });
  });
  return {
    done,
    cancel() {
      cancelled = true;
      terminate(child);
    },
    enterCode(code) {
      child.stdin?.write(`${code.trim()}\n`);
    },
  };
}

/** What the CLI said last, without the long link. */
const lastWords = (output: string) =>
  output
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line && !/https?:\/\//.test(line))
    .at(-1);
