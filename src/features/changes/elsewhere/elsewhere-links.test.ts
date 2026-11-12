import { describe, expect, it } from "vitest";
import type { ElsewhereGroup } from "../../../../shared/elsewhere";
import { elsewhereFileOf, elsewhereFolderOf } from "./elsewhere-links";

const group = (root: string, paths: string[], name = root.split("/").at(-1)!) =>
  ({
    root,
    name,
    branch: "main",
    revision: "abc",
    files: paths.map((path) => ({
      path,
      additions: 1,
      deletions: 0,
      by: [],
      own: true,
    })),
  }) as unknown as ElsewhereGroup;

const groups = [
  group("/home/v/env-config", ["anydesk-omarchy.sh", "scripts/setup.sh"]),
  group("/home/v/dotfiles", ["scripts/setup.sh"], "My dotfiles"),
];

describe("elsewhereFileOf", () => {
  it("finds a file by its bare name, its path, or its repository's folder and path", () => {
    const pick = { root: "/home/v/env-config", path: "anydesk-omarchy.sh" };
    for (const path of ["anydesk-omarchy.sh", "env-config/anydesk-omarchy.sh"])
      expect(elsewhereFileOf({ path, directory: false }, groups)).toEqual(pick);
    expect(
      elsewhereFileOf(
        { path: "env-config/scripts/setup.sh", directory: false },
        groups,
      ),
    ).toEqual({ root: "/home/v/env-config", path: "scripts/setup.sh" });
  });

  it("finds one by its absolute path", () => {
    expect(
      elsewhereFileOf(
        {
          path: "/home/v/dotfiles/scripts/setup.sh",
          directory: false,
          outside: true,
        },
        groups,
      ),
    ).toEqual({ root: "/home/v/dotfiles", path: "scripts/setup.sh" });
  });

  it("finds none when the name fits files in two repositories", () => {
    expect(
      elsewhereFileOf({ path: "setup.sh", directory: false }, groups),
    ).toBeUndefined();
  });
});

describe("elsewhereFolderOf", () => {
  it("finds a repository by its folder's name or the name Relay knows it by", () => {
    expect(
      elsewhereFolderOf({ path: "env-config", directory: true }, groups),
    ).toBe("/home/v/env-config");
    expect(
      elsewhereFolderOf({ path: "My dotfiles", directory: true }, groups),
    ).toBe("/home/v/dotfiles");
    expect(
      elsewhereFolderOf({ path: "scripts", directory: true }, groups),
    ).toBeUndefined();
  });
});
