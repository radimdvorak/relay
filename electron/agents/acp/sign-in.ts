import { homedir } from "node:os";
import { withTimeout } from "../../util/timeout";
import type { AcpProfile } from "./profiles";
import { acquireAcpAgent } from "./sessions";

/** As long as Antigravity itself waits for the browser. */
const signInTimeout = 5 * 60_000;

/**
 * Has the agent sign in the way it does without a terminal, in a process of
 * its own: Antigravity opens Google's page in the browser and answers once the
 * user is through. Rejects when it can't, or the sign-in didn't finish.
 */
export async function signInAcp(profile: AcpProfile, signal?: AbortSignal) {
  const check = () => {
    if (signal?.aborted) throw new Error("Sign-in cancelled.");
  };
  check();
  await profile.setUp?.();
  check();
  const wanted = await profile.authMethod?.();
  check();
  const agent = await acquireAcpAgent(profile, undefined, homedir());
  const cancel = () => agent.close();
  signal?.addEventListener("abort", cancel, { once: true });
  try {
    if (signal?.aborted) throw new Error("Sign-in cancelled.");
    // Without a choice of Relay's, the first sign-in the agent offers.
    const method = wanted ?? agent.caps?.auth[0];
    if (!method || !agent.caps?.auth.includes(method))
      throw new Error(
        `${profile.name} can't sign in from Relay. Sign in with its CLI in a terminal.`,
      );
    await withTimeout(
      agent.connection.request("authenticate", { methodId: method }),
      signInTimeout,
      `Signing in to ${profile.name} didn't finish in time.`,
    );
  } catch (error) {
    if (signal?.aborted) throw new Error("Sign-in cancelled.");
    throw error;
  } finally {
    signal?.removeEventListener("abort", cancel);
    agent.close();
  }
}

/**
 * Says there's no browser here, so a sign-in Google no longer takes fails
 * instead of opening its page unasked.
 */
const noBrowser = {
  SSH_CONNECTION: "127.0.0.1 0 127.0.0.1 0",
  DISPLAY: "",
  WAYLAND_DISPLAY: "",
};

/**
 * Has Antigravity renew a saved Google sign-in, in a process of its own that
 * opens no session, so it uses no model quota.
 */
export async function renewAcpSignIn(profile: AcpProfile) {
  const agent = await acquireAcpAgent(profile, undefined, homedir(), {
    env: noBrowser,
  });
  try {
    await withTimeout(
      agent.connection.request("authenticate", {
        methodId: "oauth-personal",
      }),
      30_000,
      `${profile.name} didn't renew its sign-in in time.`,
    );
  } finally {
    agent.close();
  }
}

/** Signs the agent out through ACP's `logout`, for agents that answer it. */
export async function signOutAcp(profile: AcpProfile) {
  const agent = await acquireAcpAgent(profile, undefined, homedir());
  try {
    if (!agent.caps?.logout)
      throw new Error(`${profile.name} can't sign out from Relay.`);
    await withTimeout(
      agent.connection.request("logout", {}),
      30_000,
      `Signing out of ${profile.name} didn't finish in time.`,
    );
  } finally {
    agent.close();
  }
}
