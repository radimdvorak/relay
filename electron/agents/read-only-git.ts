/**
 * Git settings added to a read-only turn's agent process. A checkout can
 * commit a bare repository whose config sets `core.worktree` and a
 * `core.fsmonitor` command, which `git -C that.git status` runs. With
 * `safe.bareRepository=explicit` git opens a bare repository only when told
 * to with `--git-dir`. `core.fsmonitor=false` keeps a checkout's own
 * `.git/config` from naming a command for `git status` to run, and the
 * signature programs go back to Git's defaults so that config can't name
 * one for `git log` to run on a signed commit. The settings go after any
 * the environment already passes. They ride in `GIT_CONFIG_PARAMETERS`
 * rather than `GIT_CONFIG_KEY_<n>`: Codex drops names containing KEY from a shell's
 * environment but keeps the count, and git then refuses every command.
 * Each is spelled `'key=value'`, not `'key'='value'`: git before 2.31
 * reads only the first and refuses every command on the second.
 */
export function readOnlyGitEnv(
  base: Record<string, string | undefined>,
): Record<string, string> {
  const settings = [
    "safe.bareRepository=explicit",
    "core.fsmonitor=false",
    "gpg.program=gpg",
    "gpg.ssh.program=ssh-keygen",
    "gpg.x509.program=gpgsm",
  ]
    .map((setting) => `'${setting}'`)
    .join(" ");
  const inherited = base.GIT_CONFIG_PARAMETERS?.trim();
  return {
    GIT_CONFIG_PARAMETERS: inherited ? `${inherited} ${settings}` : settings,
  };
}
