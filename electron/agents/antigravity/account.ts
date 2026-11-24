import { access, readFile, stat } from "node:fs/promises";
import { createHash } from "node:crypto";
import { homedir } from "node:os";
import { join } from "node:path";
import { runExecutable } from "../../platform/executables";

export const antigravityHome = (env: NodeJS.ProcessEnv = process.env) =>
  env.GEMINI_HOME || join(homedir(), ".gemini");

/** The folder Relay's Antigravity keeps its settings, sign-in and history in, under `home`. */
export const appDataDir = "antigravity-acp";

/** Where the CLI keeps a Google sign-in when it doesn't use the Keychain. */
export const tokenFile = (home: string) =>
  join(home, appDataDir, "antigravity-oauth-token");

/** The CLI's one Keychain item, shared by every profile on the computer. */
export const keychainItem = ["-s", "gemini", "-a", "antigravity"];

export async function antigravityAuthMethod(home = antigravityHome()) {
  const settings = await readFile(
    join(home, appDataDir, "settings.json"),
    "utf8",
  )
    .then((text) => JSON.parse(text) as { auth?: { type?: unknown } })
    .catch(() => undefined);
  return typeof settings?.auth?.type === "string"
    ? settings.auth.type
    : undefined;
}

const exists = (path: string) =>
  access(path).then(
    () => true,
    () => false,
  );

/** Whether the Keychain holds the CLI's sign-in, never reading the secret; undefined when it can't tell. */
async function inKeychain() {
  const found = await runExecutable(
    "/usr/bin/security",
    ["find-generic-password", ...keychainItem],
    5000,
  ).catch(() => undefined);
  if (found?.code === 0) return true;
  return found?.code === 44 ? false : undefined;
}

/**
 * Checks only whether a Google sign-in exists. With `fileStorage` the CLI
 * keeps it in a file; otherwise in the system's keyring, which Relay can only
 * look into on macOS. Elsewhere it can't tell, and the first turn says.
 */
export async function readAntigravityAccount(
  home = antigravityHome(),
  fileStorage = !!process.env.AGY_ACP_FORCE_FILE_STORAGE,
): Promise<{ signedIn: boolean } | undefined> {
  const choice = await antigravityAuthMethod(home);
  if (choice && choice !== "oauth-personal" && choice !== "oauth-business")
    return undefined;
  if (await exists(tokenFile(home))) return { signedIn: true };
  if (fileStorage) return { signedIn: false };
  if (process.platform !== "darwin") return undefined;
  const found = await inKeychain();
  return found === undefined ? undefined : { signedIn: found };
}

/** Notices external sign-ins/settings changes without reading a Keychain secret. */
export async function antigravityCredentialRevision(
  home: string,
  fileStorage: boolean,
) {
  const parts = await Promise.all(
    [join(home, appDataDir, "settings.json"), tokenFile(home)].map(
      async (path) => {
        const info = await stat(path).catch(() => undefined);
        return info
          ? `${path}:${info.ino}:${info.size}:${info.mtimeMs}:${info.ctimeMs}`
          : path;
      },
    ),
  );
  if (process.platform === "darwin" && !fileStorage) {
    const metadata = await runExecutable(
      "/usr/bin/security",
      ["find-generic-password", ...keychainItem],
      5000,
    ).catch(() => undefined);
    parts.push(metadata?.stdout ?? "");
  }
  return createHash("sha256").update(parts.join("\0")).digest("hex");
}
