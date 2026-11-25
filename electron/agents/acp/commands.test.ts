import { expect, it } from "vitest";
import { hearCommands, heardCommands, heardInFolder } from "./commands";

it("hears `$name` commands as skills and the rest as commands, once per name", () => {
  hearCommands("acp:fixture", {
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
  expect(heardCommands("acp:fixture")).toEqual([
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
      "acp:fixture",
      {
        sessionUpdate: "available_commands_update",
        availableCommands: [{ name }],
      },
      { cwd },
    );
  list("skill-of-a", "/a");
  list("skill-of-b", "/b");
  expect(heardInFolder("acp:fixture", "/a")?.map((c) => c.name)).toEqual([
    "skill-of-a",
  ]);
  expect(heardInFolder("acp:fixture", "/c")).toBeUndefined();
  for (let i = 0; i < 60; i++) list("helper", `/tmp/helper-${i}`);
  expect(heardInFolder("acp:fixture", "/a")).toBeUndefined();
});
