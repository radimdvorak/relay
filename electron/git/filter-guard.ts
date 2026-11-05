import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { refreshesIndex } from "./fsmonitor-guard";

const exec = promisify(execFile);

/**
 * The commands Git LFS installs. They run the `git-lfs` on PATH, not
 * anything the repository brought, and without them a polled status would
 * hash a touched LFS file whole and call it modified.
 */
const lfsCommands = new Set([
  "git-lfs clean -- %f",
  "git-lfs smudge -- %f",
  "git-lfs filter-process",
]);

/** Config the person set up themselves; the rest came with the folder. */
const ownScopes = new Set(["system", "global", "command"]);

const RECHECK_MS = 60_000;

type FilterCheck = {
  at: number;
  keys: string[] | undefined;
  done: Promise<string[]>;
};

const checks = new Map<string, FilterCheck>();

/**
 * Filter keys from the repository's own config (`.git/config`, a worktree's
 * config, or a file either includes) that name a command Relay didn't vet.
 */
async function foreignFilterKeys(
  file: string,
  root: string,
  env: NodeJS.ProcessEnv,
) {
  const listed = await exec(
    file,
    [
      "-C",
      root,
      "config",
      "--show-scope",
      "-z",
      "--get-regexp",
      "^filter\\..*\\.(clean|smudge|process)$",
    ],
    { env, timeout: 15000 },
  ).then(
    ({ stdout }) => stdout,
    // None set exits 1; config Git can't read fails the read itself too.
    () => "",
  );
  const fields = listed.split("\0");
  const keys: string[] = [];
  for (let i = 0; i + 1 < fields.length; i += 2) {
    const scope = fields[i];
    const [key, command = ""] = fields[i + 1].split(/\n(.*)/s);
    if (ownScopes.has(scope) || lfsCommands.has(command)) continue;
    keys.push(key);
  }
  return keys;
}

/** The last answer for `root`, asking again once it's a minute old. */
function checkFilters(file: string, root: string, env: NodeJS.ProcessEnv) {
  const last = checks.get(root);
  if (last && Date.now() - last.at < RECHECK_MS) return last;
  const done = foreignFilterKeys(file, root, env);
  const check: FilterCheck = { at: Date.now(), keys: last?.keys, done };
  checks.set(root, check);
  void done.then((keys) => (check.keys = keys));
  return check;
}

/**
 * Each key emptied, which passes content through untouched. `-c` can't
 * spell a key with `=` in it, as Git splits at the first one, so a read in
 * a repository with such a filter is refused instead.
 */
function emptied(keys: string[]) {
  const settings: string[] = [];
  for (const key of keys) {
    if (key.includes("="))
      throw new Error(
        `This repository's config names a filter Relay can't switch off for a background read (${key}).`,
      );
    const name = key.slice("filter.".length, key.lastIndexOf("."));
    settings.push("-c", `${key}=`, "-c", `filter.${name}.required=false`);
  }
  return settings;
}

/**
 * The `-c` settings that keep a polled read from running the clean, smudge
 * or process filters the repository's own config names. Known once a root
 * has been checked, so Git starts before `git()` returns; the first read
 * of a root waits for the answer. Commands the person asked for (commit,
 * checkout) keep the repository's filters.
 */
export function filterOverride(
  file: string,
  root: string,
  args: string[],
  env: NodeJS.ProcessEnv,
): string[] | Promise<string[]> {
  if (!refreshesIndex(args)) return [];
  const check = checkFilters(file, root, env);
  return check.keys ? emptied(check.keys) : check.done.then(emptied);
}
