// OpenCode Go's plan limits. OpenCode 2's local service already reads them for
// its own /usage, with the sign-in it owns, so Relay asks the service and never
// sees a key. The route is experimental: when it changes, the ring says it
// couldn't read usage and nothing else breaks.
//
// Reading limits never starts OpenCode or changes its configuration. The SDK's
// discover() reads the service's registration file once, probes the service it
// names and hands back its address and password together, so a restart can't
// pair one service's address with another's password, and no process is run.
// It looks where released OpenCode registers, service.json in the state folder;
// a build on a channel of its own registers elsewhere and reads as not running.
import { homedir } from "node:os";
import { discover } from "@opencode/client/service";
import { z } from "zod";
import {
  SESSION_MS,
  WEEK_MS,
  type ProviderUsage,
  type UsageWindow,
} from "../../../shared/provider-usage";
import { localServiceUrl } from "./native-api";

const REQUEST_TIMEOUT = 10_000;
const LIMITS_PATH = "/api/experimental/usage/limits";
// The furthest moments a JavaScript Date can hold.
const DATE_LIMIT = 8.64e15;

type Service = NonNullable<Awaited<ReturnType<typeof discover>>>;
type Go = z.infer<typeof goSchema>;
type Meter = z.infer<typeof meterSchema>;

const meterSchema = z.object({
  percent: z.number(),
  /** Go refuses requests until the window resets. */
  limited: z.boolean(),
  resetsAt: z.number().int().min(-DATE_LIMIT).max(DATE_LIMIT),
});

const goSchema = z.object({
  rolling: meterSchema,
  weekly: meterSchema,
  monthly: meterSchema.refine((meter) =>
    Number.isFinite(monthBefore(meter.resetsAt)),
  ),
});

// What the service answers when it can read the Console: `data` is null when
// OpenCode has no workspace sign-in to ask with, otherwise it names the
// workspace and its budgets, and carries `go` only when Go reports limits for
// it. An answer without that envelope isn't the service's.
const limitsSchema = z.object({
  data: z
    .object({
      workspace: z.object({ id: z.string() }),
      budgets: z.array(z.unknown()),
      go: goSchema.optional(),
    })
    .nullable(),
});

const empty = (message: string): ProviderUsage => ({
  provider: "opencode",
  windows: [],
  message,
});

/** The Go plan's limits, for the usage ring. */
export async function readOpenCodeUsage(): Promise<ProviderUsage> {
  const service = await discover();
  if (!service) return empty("Shows while OpenCode is running");
  const response = await askLimits(service);
  if (response.status === 404) {
    return empty("This OpenCode doesn't report Go limits");
  }
  if (!response.ok)
    throw new Error(`OpenCode usage failed: ${response.status}`);
  const answer = limitsSchema.safeParse(
    await response.json().catch(() => null),
  );
  if (!answer.success) return empty("Couldn't read usage");
  const limits = answer.data.data;
  if (!limits) return empty("Go usage needs a Console workspace sign-in");
  if (!limits.go) return empty("No Go limits reported for this workspace");
  return {
    provider: "opencode",
    windows: openCodeGoWindows(limits.go),
    message: null,
  };
}

function askLimits(service: Service) {
  const url = new URL(LIMITS_PATH, localServiceUrl(service.url));
  url.searchParams.set("location[directory]", homedir());
  const headers: Record<string, string> = { accept: "application/json" };
  if (service.auth) {
    const { username, password } = service.auth;
    const login = Buffer.from(`${username}:${password}`).toString("base64");
    headers.authorization = `Basic ${login}`;
  }
  return fetch(url, {
    headers,
    redirect: "manual",
    signal: AbortSignal.timeout(REQUEST_TIMEOUT),
  });
}

/**
 * Go's three limits, each resetting when OpenCode says: the 5-hour window,
 * the week (Monday 00:00 UTC) and the paid month. Go doesn't say when the
 * month began, so that window's length is a guess for the pace.
 */
export function openCodeGoWindows(go: Go): UsageWindow[] {
  const { monthly } = go;
  return [
    goWindow("session", go.rolling, SESSION_MS),
    goWindow("weekly", go.weekly, WEEK_MS),
    {
      ...goWindow(
        "monthly",
        monthly,
        monthly.resetsAt - monthBefore(monthly.resetsAt),
      ),
      periodEstimated: true,
    },
  ];
}

function goWindow(
  kind: UsageWindow["kind"],
  meter: Meter,
  periodMs: number,
): UsageWindow {
  const percent = Math.min(100, Math.max(0, meter.percent));
  return {
    kind,
    usedPercent: meter.limited ? 100 : percent,
    resetsAt: meter.resetsAt,
    periodMs,
  };
}

/**
 * The same moment a calendar month earlier, in UTC; NaN past what a Date holds.
 * A billing day the shorter month lacks is the shorter month's last day.
 */
export function monthBefore(at: number) {
  const end = new Date(at);
  const start = new Date(at);
  // From the 1st, so a 31st doesn't spill into the month after.
  start.setUTCDate(1);
  start.setUTCMonth(end.getUTCMonth() - 1);
  const lastDay = new Date(
    Date.UTC(start.getUTCFullYear(), start.getUTCMonth() + 1, 0),
  ).getUTCDate();
  start.setUTCDate(Math.min(end.getUTCDate(), lastDay));
  return start.getTime();
}
