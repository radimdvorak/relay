import { afterEach, beforeEach, expect, it } from "vitest";
import { mkdir, mkdtemp, realpath, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { performGitAction } from "./git-actions";
import { workingTree } from "./working-tree";
import { gitIn } from "../../tests/fixtures/git-repo";

let root: string;
const git = (...args: string[]) => gitIn(root, ...args);

beforeEach(async () => {
  root = await realpath(await mkdtemp(join(tmpdir(), "relay-commit-")));
  git("init", "-q", "-b", "main");
  git("config", "user.name", "Test");
  git("config", "user.email", "test@example.invalid");
  git("commit", "-q", "--allow-empty", "-m", "Initial");
});

afterEach(() => rm(root, { recursive: true, force: true }));

it("commits more picked files than fit on a command line", async () => {
  // Windows refuses a command line past 32k characters, macOS past 1MB:
  // 1,500 paths of ~740 characters are past both.
  const folder = Array.from({ length: 3 }, (_, i) => `${i}`.repeat(240)).join(
    "/",
  );
  await mkdir(join(root, folder), { recursive: true });
  const paths = Array.from(
    { length: 1500 },
    (_, i) => `${folder}/file-${String(i).padStart(4, "0")}.ts`,
  );
  await Promise.all(paths.map((p) => writeFile(join(root, p), p)));
  await writeFile(join(root, "left-out.ts"), "not picked");
  const tree = await workingTree(root);

  await performGitAction(root, {
    kind: "commit",
    revision: tree.revision,
    message: "Many files",
    paths,
  });

  expect(git("diff", "--shortstat", "HEAD~", "HEAD")).toMatch(
    /^1500 files changed/,
  );
  expect(git("status", "--porcelain")).toBe("?? left-out.ts");
}, 60000);
