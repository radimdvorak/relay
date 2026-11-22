// OpenCode Go's plan limits. OpenCode 2's local service already reads them for
// its own /usage, with the sign-in it owns, so Relay asks the service and never
// sees a key. The route is experimental: when it changes, the ring says it
// couldn't read usage and nothing else breaks. Only a service that is already
// running is asked, so reading limits never starts OpenCode.
import { homedir } from "node:os";
import { z } from "zod";
import { NotInstalledError } from "../../platform/executables";
import {
  SESSION_MS,
  WEEK_MS,
  type ProviderUsage,
  type UsageWindow,
} from "../../../shared/provider-usage";
import { OpenCodeStoppedError, openCodeNativeApi } from "./native-api";

const meterSchema = z.object({
  percent: z.number(),
  /** Go refuses requests until the window resets. */
  limited: z.boolean(),
  resetsAt: z.number(),
});

const goSchema = z.object({
  rolling: meterSchema,
  weekly: meterSchema,
  monthly: meterSchema,
});

// `data` is null when OpenCode isn't signed in to a Console workspace, and
// `go` is left out when that workspace has no Go subscription.
const limitsSchema = z.object({
  data: z.object({ go: goSchema.optional() }).nullable(),
});

type Meter = z.infer<typeof meterSchema>;
type Go = z.infer<typeof goSchema>;

const empty = (message: string): ProviderUsage => ({
  provider: "opencode",
  windows: [],
  message,
});

/** The Go plan's limits, for the usage ring. */
export async function readOpenCodeUsage(): Promise<ProviderUsage> {
  const query = new URLSearchParams({ "location[directory]": homedir() });
  let answer: unknown;
  try {
    answer = await openCodeNativeApi(
      "GET",
      `/api/experimental/usage/limits?${query}`,
      undefined,
      { passive: true },
    );
  } catch (error) {
    if (error instanceof NotInstalledError) {
      return empty("OpenCode isn't installed");
    }
    if (error instanceof OpenCodeStoppedError) {
      return empty("Shows while OpenCode is running");
    }
    throw error;
  }
  const parsed = limitsSchema.safeParse(answer);
  if (!parsed.success) return empty("Couldn't read usage");
  const limits = parsed.data.data;
  if (!limits) return empty("Sign in with OpenCode");
  if (!limits.go) return empty("No OpenCode Go plan");
  return {
    provider: "opencode",
    windows: openCodeGoWindows(limits.go),
    message: null,
  };
}

/**
 * Go's three limits, each resetting when OpenCode says: the 5-hour window,
 * the week (Monday 00:00 UTC) and the paid month.
 */
export function openCodeGoWindows(go: Go): UsageWindow[] {
  const { monthly } = go;
  return [
    goWindow("session", go.rolling, SESSION_MS),
    goWindow("weekly", go.weekly, WEEK_MS),
    goWindow(
      "monthly",
      monthly,
      monthly.resetsAt - monthBefore(monthly.resetsAt),
    ),
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
 * The same moment a calendar month earlier, in UTC. A month has no fixed
 * length, and the paid month is the one pace is measured over.
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
