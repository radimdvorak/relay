import type { QuotaCredential } from "./usage";

const minute = 60_000;

/** How long each failure in a row holds off the next try. */
const holdOffs = [1, 5, 15, 60].map((minutes) => minutes * minute);

/** About to run out counts too: a read takes a moment. */
export const hasRunOut = (credential: QuotaCredential, now: number) =>
  credential.expiresAt <= now + minute;

/**
 * Has the CLI renew a saved sign-in whose access token ran out, and reads it
 * again. Failures hold off further tries, each longer than the last, until
 * one works or someone signs in as someone else.
 */
export function createSignInRenewal(
  renew: () => Promise<void>,
  read: () => Promise<QuotaCredential | undefined>,
  now = Date.now,
) {
  let failed = { fingerprint: "", times: 0, retryAt: 0 };
  let running: Promise<QuotaCredential | undefined> | undefined;

  const attempt = async (expired: QuotaCredential) => {
    await renew().catch((error: Error) =>
      console.warn("Could not renew the Antigravity sign-in:", error.message),
    );
    const renewed = await read();
    if (renewed && !hasRunOut(renewed, now())) {
      failed = { fingerprint: "", times: 0, retryAt: 0 };
      return renewed;
    }
    const { fingerprint } = expired;
    const times = failed.fingerprint === fingerprint ? failed.times + 1 : 1;
    const holdOff = holdOffs[Math.min(times, holdOffs.length) - 1]!;
    failed = { fingerprint, times, retryAt: now() + holdOff };
    return renewed;
  };

  return (expired: QuotaCredential): Promise<QuotaCredential | undefined> => {
    const heldOff =
      failed.fingerprint === expired.fingerprint && now() < failed.retryAt;
    if (!expired.renewable || heldOff) return Promise.resolve(expired);
    running ??= attempt(expired).finally(() => (running = undefined));
    return running;
  };
}
