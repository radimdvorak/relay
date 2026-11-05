import {
  filtersEmptied,
  foreignFilterKeys,
} from "../git-config/repository-filters";
import { refreshesIndex } from "./fsmonitor-guard";

const RECHECK_MS = 60_000;

type FilterCheck = {
  at: number;
  keys: string[] | undefined;
  done: Promise<string[]>;
};

const checks = new Map<string, FilterCheck>();

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
  return check.keys
    ? filtersEmptied(check.keys)
    : check.done.then(filtersEmptied);
}
