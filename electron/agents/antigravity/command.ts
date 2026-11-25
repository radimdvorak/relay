import { appDataDir } from "./account";
import type { InstalledAntigravity } from "./install";

/** The command line for Google's Antigravity CLI, with its folders under `home`. */
export function antigravityCommand(cli: InstalledAntigravity, home: string) {
  return {
    command: cli.command,
    args: [
      ...cli.args,
      "acp",
      "--gemini-dir",
      home,
      "--app-data-dir",
      appDataDir,
    ],
    env: {
      // Relay updates it; one updating itself would swap the binary under live sessions.
      AGY_CLI_DISABLE_AUTO_UPDATE: "1",
      // Otherwise every profile shares one Keychain item, and processes that
      // start together on an expired token lose it renewing it at once.
      AGY_CLI_FORCE_FILE_TOKEN_STORAGE: "1",
    },
  };
}
