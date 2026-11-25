import { readFile, stat } from "node:fs/promises";
import { createHash } from "node:crypto";
import { homedir } from "node:os";
import { join } from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import { z } from "zod";

export const antigravityHome = (env: NodeJS.ProcessEnv = process.env) =>
  env.GEMINI_HOME || join(homedir(), ".gemini");

/** The folder Relay's Antigravity keeps its settings, sign-in and history in, under `home`. */
export const appDataDir = "antigravity-acp";

/** Where the CLI keeps the profile's Google sign-in. */
const tokenFile = (home: string) =>
  join(home, appDataDir, "antigravity-oauth-token");

const settingsFile = (home: string) => join(home, appDataDir, "settings.json");

/** How the CLI saves a Google sign-in: Go's oauth2 token. */
const savedLoginSchema = z.object({
  token: z.object({
    access_token: z.string().min(1),
    refresh_token: z.string().min(1).optional(),
    expiry: z.string().optional(),
  }),
});

export async function antigravityAuthMethod(home = antigravityHome()) {
  const settings = await readFile(settingsFile(home), "utf8")
    .then((text) => JSON.parse(text) as { auth?: { type?: unknown } })
    .catch(() => undefined);
  return typeof settings?.auth?.type === "string"
    ? settings.auth.type
    : undefined;
}

/** Whether the profile uses a Google sign-in, as it does until it chose a key. */
export async function usesGoogleSignIn(home: string) {
  const method = await antigravityAuthMethod(home);
  return !method || method === "oauth-personal" || method === "oauth-business";
}

async function readTokenFile(home: string): Promise<unknown> {
  const path = tokenFile(home);
  const info = await stat(path).catch(() => undefined);
  if (!info?.isFile() || info.size > 64_000) return undefined;
  const parse = async (): Promise<unknown> =>
    JSON.parse(await readFile(path, "utf8"));
  // The CLI may be halfway through writing a renewed token.
  return parse()
    .catch(() => sleep(100).then(parse))
    .catch(() => undefined);
}

/** The profile's saved Google sign-in; the CLI rewrites it as it renews the access token. */
export async function readSavedLogin(home: string) {
  const saved = await readTokenFile(home);
  return savedLoginSchema.safeParse(saved).data?.token;
}

/** Names a sign-in without its secrets; it stays the same as the access token is renewed. */
export const loginFingerprint = (login: {
  access_token: string;
  refresh_token?: string;
}) =>
  createHash("sha256")
    .update(login.refresh_token ?? login.access_token)
    .digest("hex");

/** Whether a Google sign-in is saved; undefined for a profile that uses another kind. */
export async function readAntigravityAccount(
  home = antigravityHome(),
): Promise<{ signedIn: boolean } | undefined> {
  if (!(await usesGoogleSignIn(home))) return undefined;
  return { signedIn: (await readSavedLogin(home)) !== undefined };
}

/**
 * Changes when someone signs in, out or as someone else, or picks another way
 * to. Not when the CLI renews its access token, which it does every hour:
 * threads' processes restart on a change, ending what runs in them.
 */
export async function antigravityCredentialRevision(home: string) {
  const [settings, login] = await Promise.all([
    readFile(settingsFile(home), "utf8").catch(() => ""),
    readSavedLogin(home),
  ]);
  return createHash("sha256")
    .update(settings)
    .update("\0")
    .update(login ? loginFingerprint(login) : "")
    .digest("hex");
}
