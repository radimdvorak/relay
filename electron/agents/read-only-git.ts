/**
 * Git settings added to a read-only turn's agent process. A checkout can
 * commit a bare repository whose config sets `core.worktree` and a
 * `core.fsmonitor` command, which `git -C that.git status` runs. With
 * `safe.bareRepository=explicit` git opens a bare repository only when told
 * to with `--git-dir`. The setting goes after any the environment already
 * passes. It rides in `GIT_CONFIG_PARAMETERS` rather than
 * `GIT_CONFIG_KEY_<n>`: Codex drops names containing KEY from a shell's
 * environment but keeps the count, and git then refuses every command.
 */
export function readOnlyGitEnv(
  base: Record<string, string | undefined>,
): Record<string, string> {
  const setting = "'safe.bareRepository'='explicit'";
  const inherited = base.GIT_CONFIG_PARAMETERS?.trim();
  return {
    GIT_CONFIG_PARAMETERS: inherited ? `${inherited} ${setting}` : setting,
  };
}
