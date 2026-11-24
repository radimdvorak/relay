import { appDataDir } from "./account";
import type { InstalledAntigravity } from "./install";

/**
 * The command line for Google's Antigravity CLI, with its folders under
 * `home`. `fileStorage` keeps the sign-in in a file of that profile's: the
 * CLI otherwise uses one Keychain item for every profile, and only leaves it
 * alone when it thinks it runs over SSH.
 */
export function antigravityCommand(
  cli: InstalledAntigravity,
  home: string,
  fileStorage: boolean,
  extra: string[] = [],
) {
  const env: Record<string, string> = {
    // Relay updates it; one updating itself would swap the binary under live sessions.
    AGY_CLI_DISABLE_AUTO_UPDATE: "1",
  };
  if (fileStorage)
    env.SSH_CONNECTION =
      process.env.SSH_CONNECTION || "127.0.0.1 0 127.0.0.1 0";
  return {
    command: cli.command,
    args: [
      ...cli.args,
      "acp",
      ...extra,
      "--gemini-dir",
      home,
      "--app-data-dir",
      appDataDir,
    ],
    env,
  };
}
