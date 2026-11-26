import { homedir } from "node:os";
import { expect, it } from "vitest";
import { hearCommands, heardCommands, heardInFolder } from "./commands";

const fixture = { provider: "acp:fixture" } as const;

it("hears `$name` commands as skills and the rest as commands, once per name", () => {
  hearCommands(fixture, {
    sessionUpdate: "available_commands_update",
    availableCommands: [
      {
        name: "review",
        description: "Review changes",
        input: { hint: "<branch>" },
      },
      { name: "$explain", description: "Explain code" },
      { name: "Review", description: "a duplicate" },
      { name: "$", description: "no name" },
    ],
  });
  expect(heardCommands(fixture)).toEqual([
    {
      name: "review",
      description: "Review changes",
      source: "other",
      argumentHint: "<branch>",
    },
    {
      name: "explain",
      skill: true,
      source: "other",
      description: "Explain code",
    },
  ]);
});

it("keeps one folder's list apart from another's", () => {
  const list = (name: string, cwd: string) =>
    hearCommands(
      fixture,
      {
        sessionUpdate: "available_commands_update",
        availableCommands: [{ name }],
      },
      { cwd },
    );
  list("at-home", homedir());
  list("skill-of-a", "/a");
  list("skill-of-b", "/b");
  expect(heardInFolder(fixture, "/a")?.map((c) => c.name)).toEqual([
    "skill-of-a",
  ]);
  expect(heardInFolder(fixture, "/c")).toBeUndefined();
  for (let i = 0; i < 60; i++) list("helper", `/tmp/helper-${i}`);
  expect(heardInFolder(fixture, "/a")).toBeUndefined();
  // The home folder's list is what a folder never opened offers, so it stays.
  expect(heardInFolder(fixture, homedir())?.map((c) => c.name)).toEqual([
    "at-home",
  ]);
});

it("keeps one account's list apart from another's in the same folder", () => {
  const account = (accountKey: string) => ({
    provider: "antigravity" as const,
    accountKey,
  });
  const update = {
    sessionUpdate: "available_commands_update",
    availableCommands: [{ name: "deploy" }],
  };
  hearCommands(account("a"), update, { cwd: "/repo" });
  expect(heardInFolder(account("a"), "/repo")).toHaveLength(1);
  expect(heardInFolder(account("b"), "/repo")).toBeUndefined();
  expect(heardCommands(account("b"))).toBeUndefined();
});
