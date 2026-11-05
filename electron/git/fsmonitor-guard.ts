import { execFile } from "node:child_process";
import { promisify } from "node:util";

const exec = promisify(execFile);

/**
 * Reads that refresh the index, and so run the repository's `core.fsmonitor`.
 * Relay polls these, and a folder someone handed over brings its own
 * `.git/config`, so a hook command named there would run on every poll.
 */
const indexReads = new Set([
  "status",
  "diff",
  "diff-index",
  "diff-files",
  "ls-files",
  "check-ignore",
  "grep",
  "blame",
]);

const monitorOff = ["-c", "core.fsmonitor=false"];
const RECHECK_MS = 60_000;

type MonitorCheck = {
  at: number;
  hook: boolean | undefined;
  done: Promise<boolean>;
};

const checks = new Map<string, MonitorCheck>();

/** The Git subcommand in `args`, past any `-c key=value` settings. */
function subcommandOf(args: string[]) {
  for (let i = 0; i < args.length; i++) {
    if (args[i] === "-c") i++;
    else if (!args[i].startsWith("-")) return args[i];
  }
}

/**
 * Whether `root`'s `core.fsmonitor` names a hook command. Git's builtin
 * daemon is a boolean (`true`) and stays on: it's safe, and what keeps
 * status fast in big repositories. Unset exits 1; a value that isn't a
 * boolean fails, so it is a command.
 */
export function checkMonitor(
  file: string,
  root: string,
  env: NodeJS.ProcessEnv,
) {
  const last = checks.get(root);
  if (last && Date.now() - last.at < RECHECK_MS) return last.done;
  const done = exec(
    file,
    ["-C", root, "config", "--type=bool", "--get", "core.fsmonitor"],
    { env, timeout: 15000 },
  ).then(
    () => false,
    (error: { code?: unknown }) => error.code !== 1,
  );
  const check: MonitorCheck = { at: Date.now(), hook: last?.hook, done };
  checks.set(root, check);
  void done.then((hook) => (check.hook = hook));
  return done;
}

/**
 * The `-c` settings that keep a read from running the repository's fsmonitor
 * hook. Decided without waiting, so Git still starts before `git()` returns:
 * until the check has answered, a read goes without fsmonitor at all.
 * Commands the person asked for (commit, checkout) keep the repository's own.
 */
export function monitorOverride(
  file: string,
  root: string,
  args: string[],
  env: NodeJS.ProcessEnv,
): string[] {
  if (!indexReads.has(subcommandOf(args) ?? "")) return [];
  void checkMonitor(file, root, env);
  const hook = checks.get(root)?.hook ?? true;
  return hook ? monitorOff : [];
}
