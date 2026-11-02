/**
 * Started through `npm run`, Relay carries npm's settings (nvm refuses to
 * load with them) and RELAY_DEV_URL, which would point a Relay run from the
 * terminal at this window's renderer.
 */
const relayOnly = /^(npm_|RELAY_|ELECTRON_)|^INIT_CWD$/i;

/**
 * Started from a terminal, Relay carries that terminal's description of
 * itself, and the editor picked in that session. Shells here run in Relay's
 * terminal, and their profile picks its own editor: an inherited EDITOR=vi
 * would switch zsh to vi keys.
 */
const launchingTerminal =
  /^(ITERM_|LC_TERMINAL|TERM_SESSION_ID$|TERM_PROGRAM_VERSION$|TERM_FEATURES$|COLORFGBG$|EDITOR$|VISUAL$)/;

/** Relay's own environment minus what only concerns Relay or how it was started. */
export function inheritedEnv() {
  const env: Record<string, string> = {};
  for (const [name, value] of Object.entries(process.env))
    if (
      value !== undefined &&
      !relayOnly.test(name) &&
      !launchingTerminal.test(name)
    )
      env[name] = value;
  return env;
}

/** The user's shell; a login one reads the profile that sets PATH, which apps started from the Dock lack. */
export function userShell(): [string, string[]] {
  if (process.platform === "win32") return ["powershell.exe", ["-NoLogo"]];
  const fallback = process.platform === "darwin" ? "/bin/zsh" : "/bin/bash";
  return [process.env.SHELL || fallback, ["-l"]];
}
