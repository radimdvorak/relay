import { expect, it } from "vitest";
import type { ProviderCommand } from "../../../shared/commands";
import { antigravityOffer } from "./listed-skills";

const command = (name: string): ProviderCommand => ({
  name,
  description: `about ${name}`,
  source: "other",
});

const found = ["lower", "only-on-disk"].map((name) => ({
  name,
  path: `/project/.agents/skills/${name}/SKILL.md`,
  scope: "project",
  enabled: true,
}));

const named = (offer: ProviderCommand[]) =>
  offer.map((c) => `${c.skill ? "$" : "/"}${c.name} ${c.source}`);

it("offers what the CLI listed for the folder, its skills apart from its commands", () => {
  const commands = ["compact", "lower", "from-plugin"].map(command);
  expect(
    named(antigravityOffer({ commands, forThisFolder: true }, found)),
  ).toEqual(["/compact other", "$lower project", "$from-plugin other"]);
});

it("adds the skills on disk while the CLI has only listed another folder's", () => {
  const commands = ["compact", "from-plugin"].map(command);
  expect(
    named(antigravityOffer({ commands, forThisFolder: false }, found)),
  ).toEqual([
    "/compact other",
    "$from-plugin other",
    "$lower project",
    "$only-on-disk project",
  ]);
});
