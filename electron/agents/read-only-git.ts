/**
 * Git settings added to a read-only turn's agent process. A checkout can
 * commit a bare repository whose config sets `core.worktree` and a
 * `core.fsmonitor` command, which `git -C that.git status` runs. With
 * `safe.bareRepository=explicit` git opens a bare repository only when told
 * to with `--git-dir`. The settings go after any the environment already has.
 */
export function readOnlyGitEnv(
  base: Record<string, string | undefined>,
): Record<string, string> {
  const inherited = Number(base.GIT_CONFIG_COUNT);
  const next = Number.isInteger(inherited) && inherited > 0 ? inherited : 0;
  return {
    GIT_CONFIG_COUNT: String(next + 1),
    [`GIT_CONFIG_KEY_${next}`]: "safe.bareRepository",
    [`GIT_CONFIG_VALUE_${next}`]: "explicit",
  };
}
