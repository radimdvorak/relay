import { randomUUID } from "node:crypto";
import { copyFile, rm, stat, utimes } from "node:fs/promises";
import { git } from "./git";

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
export const run = (root: string, args: string[], env?: NodeJS.ProcessEnv) =>
  git(root, args, { timeout: 30000, env });

/** Writes the worktree's tree, untracked files included, without touching the real index. */
export async function snapshotTree(root: string) {
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
      if (head) await run(root, ["read-tree", "HEAD"], env);
    }
    await run(root, [...noMonitor, ...durable, "add", "-A", "--", "."], env);
    return (
      await run(root, [...noMonitor, ...durable, "write-tree"], env)
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