import { z } from "zod";
import { SYSTEM_ACCOUNT } from "../../../shared/agent-accounts";
import {
  SESSION_MS,
  WEEK_MS,
  type ProviderUsage,
  type UsageWindow,
} from "../../../shared/provider-usage";
import { profileDir, usualHome } from "../accounts/profiles";
import {
  antigravityAuthMethod,
  loginFingerprint,
  readSavedLogin,
  usesGoogleSignIn,
} from "./account";
import { createSignInRenewal, hasRunOut } from "./renewal";

/**
 * The CLI's current access token. It saves no OAuth client, so Relay can't
 * refresh it; only the CLI can.
 */
export type QuotaCredential = {
  fingerprint: string;
  accessToken: string;
  expiresAt: number;
  /** The CLI holds what it takes to renew it without anyone signing in. */
  renewable: boolean;
};

const endpoints = [
  "https://daily-cloudcode-pa.googleapis.com",
  "https://cloudcode-pa.googleapis.com",
];

const ANTIGRAVITY_USER_AGENT = `antigravity/hub/2.24.0 (aidev_client; os_type=${process.platform}; arch=${process.arch})`;

const summaryBucketSchema = z.object({
  bucketId: z.string(),
  window: z.string().optional(),
  displayName: z.string().optional(),
  disabled: z.boolean().optional(),
  remainingFraction: z.number().optional(),
  resetTime: z.string().optional(),
});

const summaryGroupSchema = z.object({
  displayName: z.string().optional(),
  buckets: z.array(z.unknown()).optional(),
});

const summaryBodySchema = z.object({
  groups: z.array(summaryGroupSchema).optional(),
  buckets: z.array(z.unknown()).optional(),
});

const summaryEnvelopeSchema = summaryBodySchema.extend({
  response: summaryBodySchema.optional(),
});

function bucketFamily(
  bucketId: string,
  groupName: string | undefined,
  bucketName: string | undefined,
) {
  if (bucketId === "gemini-5h" || bucketId === "gemini-weekly") return "Gemini";
  if (bucketId === "3p-5h" || bucketId === "3p-weekly") return "Claude & GPT";
  return groupName || bucketName || bucketId;
}

/** Labels stay with their buckets; Gemini and the shared Claude/GPT pool are independent. */
export function mapAntigravityUsage(raw: unknown): UsageWindow[] {
  const parsed = summaryEnvelopeSchema.safeParse(raw).data;
  const body = parsed?.response ?? parsed;
  if (!body) return [];
  const groups = body.groups?.some((g) => g.buckets?.length)
    ? body.groups
    : [{ displayName: undefined, buckets: body.buckets }];
  return groups.flatMap((group) => {
    const windows = (group.buckets ?? []).flatMap((entry): UsageWindow[] => {
      const b = summaryBucketSchema.safeParse(entry).data;
      if (!b) return [];
      if (
        b.disabled ||
        b.remainingFraction === undefined ||
        !["5h", "weekly"].includes(b.window ?? "")
      )
        return [];
      const kind = b.window === "weekly" ? "weekly" : "session";
      const family = bucketFamily(b.bucketId, group.displayName, b.displayName);
      const remaining = Math.max(0, Math.min(1, b.remainingFraction));
      const reset = Date.parse(b.resetTime ?? "");
      // Untouched pools on production report a rolling reset time that moves with the clock.
      const resetsAt =
        remaining < 1 && Number.isFinite(reset) ? reset : null;
      return [
        {
          id: b.bucketId,
          label: `${family} · ${kind === "session" ? "5h" : "Weekly"}`,
          kind,
          usedPercent: (1 - remaining) * 100,
          resetsAt,
          periodMs: kind === "session" ? SESSION_MS : WEEK_MS,
        },
      ];
    });
    return windows.sort((a, b) => a.periodMs - b.periodMs);
  });
}

/**
 * Every Gemini model reports the same share of one 5h pool, so the tightest
 * one stands for all. Tab completion reports a fraction without a window.
 */
export function mapGeminiModelQuota(raw: unknown): UsageWindow[] {
  const model = z.object({
    modelProvider: z.string().optional(),
    quotaInfo: z
      .object({
        remainingFraction: z.number().optional(),
        resetTime: z.string().optional(),
      })
      .optional(),
  });
  const models = z
    .object({ models: z.record(z.string(), z.unknown()) })
    .safeParse(raw).data?.models;
  const quotas = Object.values(models ?? {}).flatMap((raw) => {
    const info = model.safeParse(raw).data;
    const fraction = info?.quotaInfo?.remainingFraction;
    const resetTime = info?.quotaInfo?.resetTime;
    const pooled = info?.modelProvider === "MODEL_PROVIDER_GOOGLE";
    if (!pooled || fraction === undefined || !resetTime) return [];
    return [
      {
        remaining: Math.max(0, Math.min(1, fraction)),
        reset: Date.parse(resetTime),
      },
    ];
  });
  if (!quotas.length) return [];
  const tightest = quotas.reduce((a, b) => (b.remaining < a.remaining ? b : a));
  // An untouched pool reports a reset that just rolls along with the clock.
  const started = tightest.remaining < 1 && Number.isFinite(tightest.reset);
  return [
    {
      id: "gemini-models-5h",
      label: "Gemini · 5h",
      kind: "session",
      usedPercent: (1 - tightest.remaining) * 100,
      resetsAt: started ? tightest.reset : null,
      periodMs: SESSION_MS,
    },
  ];
}

/** When a token runs out; never as far as anyone knows for one saved without a time, or Go's zero time. */
function expiryOf(expiry = "") {
  const at = Date.parse(expiry);
  return at > 0 ? at : Infinity;
}

/** Read afresh on every operation, so an external sign-in is picked up without restarting. */
export async function readQuotaCredential(
  home: string,
): Promise<QuotaCredential | undefined> {
  if (!(await usesGoogleSignIn(home))) return undefined;
  const method = await antigravityAuthMethod(home);
  const login = await readSavedLogin(home);
  return (
    login && {
      accessToken: login.access_token,
      expiresAt: expiryOf(login.expiry),
      fingerprint: loginFingerprint(login),
      renewable: !!login.refresh_token && method !== "oauth-business",
    }
  );
}

class QuotaAuthError extends Error {}

/** The CLI's saved token ran out, and it couldn't renew it just now. */
class QuotaExpiredError extends Error {}

type Request = (url: string, init: RequestInit) => Promise<Response>;

async function readEndpoint(
  request: Request,
  endpoint: string,
  bearer: string,
): Promise<UsageWindow[]> {
  const init = {
    method: "POST",
    headers: {
      Authorization: `Bearer ${bearer}`,
      "Content-Type": "application/json",
      // Google gates the Gemini Models group (including weekly) on a current hub version.
      "User-Agent": ANTIGRAVITY_USER_AGENT,
    },
    body: "{}",
  };
  const response = await request(
    `${endpoint}/v1internal:retrieveUserQuotaSummary`,
    init,
  );
  if (response.status === 401) throw new QuotaAuthError();
  if (!response.ok) throw new Error("Google quota request failed.");
  const windows = mapAntigravityUsage(await response.json());
  const hasGeminiPool = windows.some(
    (window) => window.id === "gemini-5h" || window.id === "gemini-weekly",
  );
  if (hasGeminiPool) return windows;
  // Fall back to the model list if the summary omits the shared Gemini pool.
  const models = await request(
    `${endpoint}/v1internal:fetchAvailableModels`,
    init,
  )
    .then((response) => (response.ok ? response.json() : null))
    .catch(() => null);
  return [...mapGeminiModelQuota(models), ...windows];
}

const unavailable = (message: string): ProviderUsage => ({
  provider: "antigravity",
  windows: [],
  message,
});

/** Reads limits with the CLI's own access token; never changes or removes a saved login. */
export function createAntigravityUsageReader(
  read: () => Promise<QuotaCredential | undefined>,
  /** Has the CLI renew the saved sign-in. */
  renew: () => Promise<void>,
  fetcher: typeof fetch = fetch,
  now = Date.now,
) {
  const request = (url: string, init: RequestInit) =>
    fetcher(url, {
      ...init,
      redirect: "manual",
      signal: AbortSignal.timeout(12_000),
    });
  const renewal = createSignInRenewal(renew, read, now);
  /** The saved sign-in, renewed if its token ran out. */
  const readCurrent = async () => {
    const saved = await read();
    if (!saved || !hasRunOut(saved, now())) return saved;
    const renewed = await renewal(saved);
    if (renewed && hasRunOut(renewed, now())) throw new QuotaExpiredError();
    return renewed;
  };
  return async (): Promise<ProviderUsage> => {
    try {
      let credential = await readCurrent();
      if (!credential)
        return unavailable("Sign in with Google to see Antigravity limits.");
      // A quota read is safe to retry once after a documented bearer-token refusal.
      for (let attempt = 0; attempt < 2; attempt++) {
        const bearer = credential.accessToken;
        const responses = await Promise.allSettled(
          endpoints.map((endpoint) =>
            readEndpoint(request, endpoint, bearer),
          ),
        );
        const windows = new Map<string, UsageWindow>();
        for (const result of responses)
          if (result.status === "fulfilled")
            for (const window of result.value) {
              const previous = windows.get(window.id!);
              if (!previous || window.usedPercent > previous.usedPercent)
                windows.set(window.id!, window);
            }
        const refused = responses.some(
          (r) => r.status === "rejected" && r.reason instanceof QuotaAuthError,
        );
        // The CLI may have renewed its token since; read it again once.
        if (refused && attempt === 0) {
          credential = await readCurrent();
          if (!credential)
            return unavailable(
              "Sign in with Google to see Antigravity limits.",
            );
          continue;
        }
        if (windows.size)
          return {
            provider: "antigravity",
            windows: [...windows.values()].slice(0, 16),
            message: null,
          };
        return unavailable(
          refused
            ? "Sign in with Google again to read Antigravity limits."
            : "Google did not report usable quota windows.",
        );
      }
    } catch (error) {
      if (error instanceof QuotaExpiredError)
        return unavailable(
          "Antigravity's sign-in needs renewing. Send it a message, then refresh.",
        );
      return unavailable(
        "Couldn't read Antigravity limits. Refresh to try again.",
      );
    }
    return unavailable("Couldn't read Antigravity limits.");
  };
}

const readers = new Map<
  string,
  ReturnType<typeof createAntigravityUsageReader>
>();

export function readAntigravityUsage(
  account: string,
  renew: (account: string) => Promise<void>,
) {
  const home =
    account === SYSTEM_ACCOUNT
      ? usualHome("antigravity")
      : profileDir("antigravity", account);
  let reader = readers.get(home);
  if (!reader)
    readers.set(
      home,
      (reader = createAntigravityUsageReader(
        () => readQuotaCredential(home),
        () => renew(account),
      )),
    );
  return reader();
}
