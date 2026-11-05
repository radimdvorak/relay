import { randomUUID } from "node:crypto";
import { copyFile, rm, stat, utimes } from "node:fs/promises";
import { exitCodeOf, git, nulSeparated } from "./git";

// Snapshots of a whole worktree as commits outside any branch, made without
// touching the real index.
const identity = {
  GIT_AUTHOR_NAME: "Relay",
  GIT_AUTHOR_EMAIL: "relay@localhost",
  GIT_COMMITTER_NAME: "Relay",
  GIT_COMMITTER_EMAIL: "relay@localhost",
};

// Git renames objects and refs into place without fsync by default, so an
// unclean restart could leave empty files under refs/relay that break fetches.
export const durable = [
  "-c",
  "core.fsync=objects,reference",
  "-c",
  "core.fsyncMethod=fsync",
];

export const noMonitor = ["-c", "core.fsmonitor=false"];

/** Snapshots walk the whole worktree, so they get twice Git's usual time. */
export const run = (
  root: string,
  args: string[],
  env?: NodeJS.ProcessEnv,
  polled = false,
) => git(root, args, { timeout: 30000, env, polled });

/**
 * Writes `paths` out of the index `env` points Git at. They go through stdin:
 * hundreds of them overflow Windows' 32k command line.
 */
export const checkOut = (
  root: string,
  paths: string[],
  env: NodeJS.ProcessEnv,
) =>
  git(root, ["checkout-index", "-f", "-z", "--stdin"], {
    timeout: 30000,
    env,
    input: nulSeparated(paths),
  });

/**
 * Writes the worktree's tree, untracked files included, without touching the
 * real index. A `polled` one is Relay's own look, which the repository's
 * filters stay out of.
 */
export async function snapshotTree(root: string, { polled = false } = {}) {
  const index = (
    await run(root, [
      "rev-parse",
      "--path-format=absolute",
      "--git-path",
      "index",
    ])
  ).trim();
  const temp = `${index}.relay-${randomUUID()}`;
  const env = { GIT_INDEX_FILE: temp, ...identity };
  try {
    // A copy of the real index keeps Git's stat cache, so `add` only rehashes
    // files that changed. Dating the copy just below the original keeps Git's
    // racy-clean check rehashing files touched in the same second.
    const seeded = await stat(index).then(
      async ({ mtimeMs }) => {
        const seconds = Math.floor((mtimeMs - 1) / 1000);
        if (seconds <= 0) return false;
        await copyFile(index, temp);
        await utimes(temp, seconds, seconds);
        return true;
      },
      () => false,
    );
    if (!seeded) {
      await rm(temp, { force: true });
      const head = await run(root, [
        "rev-parse",
        "-q",
        "--verify",
        "HEAD^{commit}",
      ]).then(
        () => true,
        () => false,
      );
      if (head) await run(root, ["read-tree", "HEAD"], env, polled);
    }
    // A folder Git can't add, like a nested repository with no commits yet,
    // is left out (exit code 1) rather than failing the whole snapshot.
    await run(
      root,
      [...noMonitor, ...durable, "add", "-A", "--ignore-errors", "--", "."],
      env,
      polled,
    ).catch((error: unknown) => {
      if (exitCodeOf(error) !== 1) throw error;
    });
    // write-tree may run the clean filter too.
    return (
      await run(root, [...noMonitor, ...durable, "write-tree"], env, polled)
    ).trim();
  } finally {
    await rm(temp, { force: true });
    await rm(`${temp}.lock`, { force: true });
  }
}

/** Commits a tree as Relay, outside any branch. */
export async function commitTree(
  root: string,
  tree: string,
  parent: string | undefined,
  message: string,
) {
  return (
    await run(
      root,
      [
        ...durable,
        "commit-tree",
        tree,
        ...(parent ? ["-p", parent] : []),
        "-m",
        message,
      ],
      identity,
    )
  ).trim();
}

/** Commits the worktree, untracked files included, without touching the real index. */
export async function snapshot(root: string, parent?: string) {
  return commitTree(
    root,
    await snapshotTree(root),
    parent,
    "Relay turn snapshot",
  );
}
