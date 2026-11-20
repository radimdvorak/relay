// Cursor's plan limits. The SDK only reports what each agent used, so Relay
// asks the dashboard API Cursor's own app reads, with the key the SDK's
// sign-in saved. Neither is documented: when they change, the ring says it
// couldn't read usage and nothing else breaks.
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import { z } from "zod";
import type {
  ProviderUsage,
  UsageWindow,
} from "../../../shared/provider-usage";

const REQUEST_TIMEOUT = 15_000;

const savedSignInSchema = z.object({
  backendUrl: z.url(),
  apiKey: z.string().min(1),
});

type SavedSignIn = z.infer<typeof savedSignInSchema>;

const epochMs = z.union([z.string(), z.number()]).transform(Number);

const periodUsageSchema = z.object({
  billingCycleStart: epochMs.optional(),
  billingCycleEnd: epochMs.optional(),
  planUsage: z
    .object({
      apiPercentUsed: z.number().optional(),
      totalPercentUsed: z.number().optional(),
    })
    .optional(),
});

const signedOut = (message: string): ProviderUsage => ({
  provider: "cursor",
  windows: [],
  message,
});

/** The plan's limits this billing cycle, for the usage ring. */
export async function readCursorUsage(): Promise<ProviderUsage> {
  const signIn = await savedSignIn();
  if (!signIn) return signedOut("Sign in with Cursor");
  const accessToken = await exchangeKey(signIn);
  if (!accessToken) return signedOut("Sign in with Cursor again");
  const usage = await currentPeriodUsage(signIn.backendUrl, accessToken);
  const windows = cursorWindows(usage);
  return {
    provider: "cursor",
    windows,
    message: windows.length ? null : "Cursor didn't say how much is left",
  };
}

/**
 * Cursor's two pools from its GetCurrentPeriodUsage answer, as its own app
 * words them: named models draw on the API pool, Auto and Composer only on
 * the total.
 */
export function cursorWindows(answer: unknown): UsageWindow[] {
  const parsed = periodUsageSchema.safeParse(answer);
  if (!parsed.success) return [];
  const usage = parsed.data;
  const start = usage.billingCycleStart;
  const end = usage.billingCycleEnd;
  if (!start || !end || end <= start) return [];
  const pools = [
    { id: "api", label: "API", used: usage.planUsage?.apiPercentUsed },
    { id: "total", label: "Total", used: usage.planUsage?.totalPercentUsed },
  ];
  return pools.flatMap(({ id, label, used }) =>
    used == null
      ? []
      : [
          {
            id,
            label,
            kind: "monthly" as const,
            usedPercent: Math.min(100, Math.max(0, used)),
            resetsAt: end,
            periodMs: end - start,
          },
        ],
  );
}

/** The key the SDK saved at sign-in; the SDK owns the file, so Relay only reads it. */
async function savedSignIn(): Promise<SavedSignIn | undefined> {
  const path = join(homedir(), ".cursor", "sdk", "auth.json");
  const text = await readFile(path, "utf8").catch(() => undefined);
  if (!text) return;
  try {
    const parsed = savedSignInSchema.safeParse(JSON.parse(text));
    return parsed.success ? parsed.data : undefined;
  } catch {
    return;
  }
}

/** A session token for the dashboard; undefined when Cursor turned the key down. */
async function exchangeKey(signIn: SavedSignIn) {
  const response = await fetch(
    `${base(signIn.backendUrl)}/auth/exchange_user_api_key`,
    {
      method: "POST",
      headers: {
        authorization: `Bearer ${signIn.apiKey}`,
        "content-type": "application/json",
      },
      body: "{}",
      signal: AbortSignal.timeout(REQUEST_TIMEOUT),
    },
  );
  if (response.status === 401 || response.status === 403) return;
  if (!response.ok)
    throw new Error(`Cursor sign-in check failed: ${response.status}`);
  const body = z
    .object({ accessToken: z.string().min(1) })
    .parse(await response.json());
  return body.accessToken;
}

async function currentPeriodUsage(backendUrl: string, accessToken: string) {
  const url = `${base(backendUrl)}/aiserver.v1.DashboardService/GetCurrentPeriodUsage`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
      "connect-protocol-version": "1",
    },
    body: "{}",
    signal: AbortSignal.timeout(REQUEST_TIMEOUT),
  });
  if (!response.ok) throw new Error(`Cursor usage failed: ${response.status}`);
  return (await response.json()) as unknown;
}

const base = (url: string) => url.replace(/\/+$/, "");
