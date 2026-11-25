import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, expect, it, vi } from "vitest";
import type { ProviderCommand } from "../../../shared/commands";

let root: string;
let home: string;

vi.mock("../accounts", () => ({
  accountFor: () => "default",
  accountLabel: () => "Default",
  accountHome: () => home,
  signedInAs: async () => undefined,
}));

const { inspectAgentTools } = await import(".");

afterEach(async () => {
  if (root) await rm(root, { recursive: true, force: true });
  if (home) await rm(home, { recursive: true, force: true });
});

async function setup() {
  root = await mkdtemp(join(tmpdir(), "relay-tools-repo-"));
  home = await mkdtemp(join(tmpdir(), "relay-tools-home-"));
  const skillDir = join(root, ".agents", "skills", "disk-skill");
  await mkdir(skillDir, { recursive: true });
  await writeFile(
    join(skillDir, "SKILL.md"),
    "---\nname: disk-skill\ndescription: From project disk\n---\n",
  );
  return { skillPath: join(skillDir, "SKILL.md") };
}

it("merges Antigravity's CLI-reported skills with paths found on disk", async () => {
  const { skillPath } = await setup();
  const known: ProviderCommand[] = [
    { name: "compact", description: "Compact", source: "other" },
    {
      name: "disk-skill",
      description: "From project disk",
      source: "project",
      skill: true,
    },
    {
      name: "plugin-skill",
      description: "From a plugin",
      source: "other",
      skill: true,
    },
  ];
  const result = await inspectAgentTools(
    "antigravity",
    root,
    async () => known,
  );
  expect(result.skills.items).toEqual([
    expect.objectContaining({
      name: "disk-skill",
      path: skillPath,
      source: "repo",
    }),
    expect.objectContaining({
      name: "plugin-skill",
      description: "From a plugin",
      state: "enabled",
    }),
  ]);
  expect(result.skills.items[1]?.path).toBeUndefined();
});

it("falls back to Antigravity's skill folders before a session has run", async () => {
  const { skillPath } = await setup();
  const result = await inspectAgentTools(
    "antigravity",
    root,
    async () => undefined,
  );
  expect(result.skills.items).toEqual([
    expect.objectContaining({ name: "disk-skill", path: skillPath }),
  ]);
});
