// The running OpenCode 2 service, found without starting it or touching its
// configuration. The SDK's discover() does the same, but it sends the service's
// password to whatever address the registration names before Relay has looked
// at the address, and its fetch follows redirects. This keeps its checks and
// orders them safely: one read of the registration file, the address held to
// a local HTTP service before any request, no redirect followed, and the
// address, the password and the identity the health check confirms all come
// from that one reading.
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import { z } from "zod";
import { localServiceUrl } from "./native-api";

// As the SDK waits for the health check of a service on this computer.
const HEALTH_TIMEOUT = 2_000;

/** What OpenCode writes to its registration file when a service starts. */
const registrationSchema = z.object({
  version: z.string().optional(),
  url: z.string(),
  pid: z.number().int().min(0),
  password: z.string().optional(),
});

/** What the service says of itself at /api/info. */
const identitySchema = z.object({
  version: z.string(),
  pid: z.number().int().min(0),
});

/** A running service as one registration read it: where, and how to sign in. */
export type OpenCodeService = {
  url: URL;
  /** The Authorization header, when the service has a password. */
  authorization?: string;
};

/** Where released OpenCode registers; a build on a channel of its own registers elsewhere. */
function registrationPath() {
  const state =
    process.env.XDG_STATE_HOME || join(homedir(), ".local", "state");
  return join(state, "opencode", "service.json");
}

/**
 * The running service, or nothing when none is registered or the one named
 * doesn't answer as itself. A registration that names an address that isn't a
 * local HTTP service throws, and nothing is sent to it.
 */
export async function runningOpenCodeService(): Promise<
  OpenCodeService | undefined
> {
  const text = await readFile(registrationPath(), "utf8").catch(
    () => undefined,
  );
  if (text === undefined) return;
  const registration = parseRegistration(text);
  if (!registration) return;
  // Before any request, since each one carries the password.
  const url = localServiceUrl(registration.url);
  const authorization =
    registration.password === undefined
      ? undefined
      : basic(registration.password);
  const identity = await askIdentity(url, authorization);
  if (!identity) return;
  // The process that registered is the one answering, as the SDK checks.
  if (identity.pid !== registration.pid) return;
  if (registration.version && identity.version !== registration.version) return;
  return { url, authorization };
}

const basic = (password: string) =>
  `Basic ${Buffer.from(`opencode:${password}`).toString("base64")}`;

function parseRegistration(text: string) {
  try {
    const parsed = registrationSchema.safeParse(JSON.parse(text));
    return parsed.success ? parsed.data : undefined;
  } catch {
    return;
  }
}

/** The service's own account of itself; a service that isn't ready, or isn't OpenCode 2, has none. */
async function askIdentity(url: URL, authorization: string | undefined) {
  const response = await fetch(new URL("/api/info", url), {
    headers: authorization ? { authorization } : {},
    // A redirect would carry the password to another address.
    redirect: "manual",
    signal: AbortSignal.timeout(HEALTH_TIMEOUT),
  }).catch(() => undefined);
  if (!response?.ok) return;
  const body = await response.json().catch(() => undefined);
  const identity = identitySchema.safeParse(body);
  return identity.success ? identity.data : undefined;
}
