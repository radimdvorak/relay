/**
 * Started through `npm run`, Relay carries npm's settings (nvm refuses to
 * load with them) and RELAY_DEV_URL, which would point a Relay run from the
 * terminal at this window's renderer.
 */
const relayOnly = /^(npm_|RELAY_|ELECTRON_)|^INIT_CWD$/i;

/** What the terminal session Relay was started from says about itself. */
const launchingTerminal =
  /^(ITERM_|LC_TERMINAL|TERM_SESSION_ID$|TERM_PROGRAM_VERSION$|TERM_FEATURES$|COLORFGBG$)/;

/**
 * The editor that session picked. Shells here run in Relay's terminal and
 * their profile picks its own: an inherited EDITOR=vi would switch zsh to vi
 * keys. Started from the Dock, a desktop session or on Windows, it's the
 * user's own setting, and stays.
 */
const sessionEditor = /^(EDITOR|VISUAL)$/;

function startedFromTerminal() {
  if (process.platform === "win32") return false;
  return Boolean(process.env.TERM_PROGRAM || process.env.TERM_SESSION_ID);
}

function leftOut(name: string, fromTerminal: boolean) {
  if (relayOnly.test(name) || launchingTerminal.test(name)) return true;
  return fromTerminal && sessionEditor.test(name);
}

/** Relay's own environment minus what only concerns Relay or how it was started. */
export function inheritedEnv() {
  const fromTerminal = startedFromTerminal();
  const env: Record<string, string> = {};
  for (const [name, value] of Object.entries(process.env))
    if (value !== undefined && !leftOut(name, fromTerminal)) env[name] = value;
  return env;
}

/** The user's shell; a login one reads the profile that sets PATH, which apps started from the Dock lack. */
export function userShell(): [string, string[]] {
  if (process.platform === "win32") return ["powershell.exe", ["-NoLogo"]];
  const fallback = process.platform === "darwin" ? "/bin/zsh" : "/bin/bash";
  return [process.env.SHELL || fallback, ["-l"]];
}
