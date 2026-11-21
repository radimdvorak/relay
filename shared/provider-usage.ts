// Subscription windows for the signed-in Claude and Codex accounts.
// Window selection and burn-rate pacing follow OpenUsage (MIT, Robin Ebers);
// see THIRD_PARTY_NOTICES.md. Claude Code reports its own; Relay asks Codex's.
import { z } from "zod";
import { usageProviderSchema, type UsageProvider } from "./agents";
import { activeWeight, nextHour, type ActiveHours } from "./usage-history";

export const SESSION_MS = 5 * 60 * 60 * 1000;
export const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

const usageWindowSchema = z.object({
  /** Providers with independent model pools name their individual windows. */
  id: z.string().max(200).optional(),
  label: z.string().max(160).optional(),
  /** Only this model's turns count against it; the rings leave it out. */
  model: z.string().max(120).optional(),
  kind: z.enum(["session", "weekly", "monthly"]),
  usedPercent: z.number().min(0).max(100),
  resetsAt: z.number().nullable(),
  periodMs: z.number().positive(),
});

const usageCreditsSchema = z.object({
  balance: z.number().min(0),
  unlimited: z.boolean(),
  /** Codex's guess at how many local messages the balance buys, low to high. */
  messages: z.tuple([z.number(), z.number()]).nullish(),
});

const savedResetsSchema = z.object({
  count: z.number().int().min(1),
  /** How many Codex would apply right now; null when it didn't say. */
  usable: z.number().int().min(0).nullable(),
  /** The ones Codex listed, first to lapse first; empty when only the count is known. */
  credits: z
    .array(
      z.object({
        id: z.string().min(1).max(200),
        expiresAt: z.number().nullable(),
      }),
    )
    .max(50),
});

export const providerUsageSchema = z
  .object({
    provider: usageProviderSchema,
    windows: z.array(usageWindowSchema).max(16),
    message: z.string().max(160).nullable(),
    /** Learned from Relay's own readings; see usage-history. */
    activeHours: z.array(z.number().min(0).max(1)).length(24).nullish(),
    /** Bought credits that carry turns once the windows run out; Codex only. */
    credits: usageCreditsSchema.nullish(),
    /** Saved resets that reset usage when spent; Codex only. */
    resets: savedResetsSchema.nullish(),
  })
  .strict();
export type UsageKind = UsageWindow["kind"];
export type UsageWindow = z.infer<typeof usageWindowSchema>;
export type ProviderUsage = z.infer<typeof providerUsageSchema>;
export type UsageCredits = z.infer<typeof usageCreditsSchema>;
export type SavedResets = z.infer<typeof savedResetsSchema>;
/** What Codex answered when asked to spend a saved reset. */
export type CodexResetOutcome =
  "reset" | "nothingToReset" | "noCredit" | "alreadyRedeemed" | "unknown";
export type CodexResetResult = {
  outcome: CodexResetOutcome;
  /** The account's limits read again afterwards. */
  usage: ProviderUsage;
};

export type MeterPace = "ok" | "warn" | "hot" | "spent";

export type UsageMeter = {
  kind: UsageKind;
  id?: string;
  label: string;
  model?: string;
  leftPercent: number;
  /** Percent that would be left now at an even burn to the reset, if known. */
  paceLeftPercent: number | null;
  pace: MeterPace;
  limitLabel: string | null;
  resetLabel: string | null;
};

const FIVE_MINUTES = 5 * 60 * 1000;
const HOUR = 60 * 60 * 1000;
// Points behind the pace mark before an early run-out turns red.
const BEHIND_HOT = 10;
const ON_PACE_SLACK = 1;

/** Rounded up to the minute, two units at most: "7m", "3h", "3h 20m", "2d 5h". */
function compactDuration(ms: number): string | null {
  if (!(ms > 0 && ms < Infinity)) return null;
  const minutes = Math.ceil(ms / 60_000) || 1;
  const hours = Math.floor(minutes / 60);
  if (hours >= 24) return `${Math.floor(hours / 24)}d ${hours % 24}h`;
  const rest = minutes % 60;
  if (hours === 0) return `${rest}m`;
  return rest ? `${hours}h ${rest}m` : `${hours}h`;
}

export function resetsIn(at: number, now: number): string | null {
  if (at - now <= FIVE_MINUTES) return "Resets soon";
  const compact = compactDuration(at - now);
  return compact ? `Resets in ${compact}` : null;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

/** Time that counts toward a limit's burn between two moments. */
type Clock = (from: number, to: number) => number;
const wallClock: Clock = (from, to) => to - from;

function clockFor(window: UsageWindow, activeHours?: ActiveHours | null) {
  // Only the week and the month span nights and weekends; a session is all working time.
  if (window.kind === "session" || !activeHours) return null;
  return ((from, to) => activeWeight(activeHours, from, to) * HOUR) as Clock;
}

const kindLabels: Record<UsageKind, string> = {
  session: "Session",
  weekly: "Weekly",
  monthly: "Monthly",
};

/**
 * Whole-percent meter for one usage window, including the pace warning. With
 * the hours someone usually works, the week's burn and pace only count those.
 */
export function presentWindow(
  window: UsageWindow,
  now: number,
  activeHours?: ActiveHours | null,
): UsageMeter {
  const used = clamp(window.usedPercent, 0, 100);
  const leftPercent = clamp(Math.round(100 - used), 0, 100);
  const label = window.label ?? kindLabels[window.kind];
  const base = { id: window.id, kind: window.kind, label, model: window.model };
  const fresh =
    window.kind === "session" && window.resetsAt == null && used <= 0;
  const resetLabel = resetLabelFor(window, fresh, now);
  const active = clockFor(window, activeHours);
  const clock = active ?? wallClock;
  const paceLeftPercent =
    window.resetsAt == null
      ? null
      : paceLeft(
          clock,
          window.resetsAt - window.periodMs,
          window.resetsAt,
          now,
        );
  if (fresh) {
    return {
      ...base,
      leftPercent: 100,
      paceLeftPercent: null,
      pace: "ok",
      limitLabel: null,
      resetLabel,
    };
  }
  if (leftPercent <= 0) {
    return {
      ...base,
      leftPercent: 0,
      paceLeftPercent,
      pace: "spent",
      limitLabel: "Limit reached",
      resetLabel,
    };
  }
  const projected = project(used, window, now, clock, active != null);
  const onPace = nearPaceMark(leftPercent, paceLeftPercent);
  if (projected?.status === "ahead" || onPace) {
    return {
      ...base,
      leftPercent,
      paceLeftPercent,
      pace: "ok",
      limitLabel: null,
      resetLabel,
    };
  }
  if (projected?.status === "onTrack") {
    // Ending the window with room left is fine, however little; the label says how much.
    const spare = Math.round(100 - projected.projected);
    return {
      ...base,
      leftPercent,
      paceLeftPercent,
      pace: "ok",
      limitLabel: spare >= 1 ? `~${spare}% spare` : null,
      resetLabel,
    };
  }
  if (projected?.status === "behind") {
    // A few points past the pace mark is one lighter afternoon, not a fire.
    const behind = (paceLeftPercent ?? 100) - leftPercent;
    return {
      ...base,
      leftPercent,
      paceLeftPercent,
      pace: behind >= BEHIND_HOT ? "hot" : "warn",
      limitLabel: projected.eta == null ? null : outIn(projected.eta),
      resetLabel,
    };
  }
  const percentUsed = Math.round(used);
  return {
    ...base,
    leftPercent,
    paceLeftPercent,
    pace: percentUsed >= 90 ? "hot" : percentUsed >= 80 ? "warn" : "ok",
    limitLabel: null,
    resetLabel,
  };
}

function resetLabelFor(window: UsageWindow, fresh: boolean, now: number) {
  if (fresh) return "Not started";
  if (window.resetsAt == null) return null;
  return resetsIn(window.resetsAt, now);
}

/**
 * How far the meter sits from its pace mark: "5% ahead" when there's more
 * left than an even burn would leave, "3% behind" when it's running hot.
 */
export function paceGap(meter: UsageMeter): string | null {
  if (meter.paceLeftPercent == null || meter.pace === "spent") return null;
  const gap = meter.leftPercent - Math.round(meter.paceLeftPercent);
  if (gap === 0) return "On pace";
  return gap > 0 ? `${gap}% ahead` : `${-gap}% behind`;
}

/** A point either side of the pace mark is spending evenly, not running hot. */
function nearPaceMark(leftPercent: number, paceLeftPercent: number | null) {
  if (paceLeftPercent == null) return false;
  return Math.abs(leftPercent - Math.round(paceLeftPercent)) <= ON_PACE_SLACK;
}

function outIn(ms: number) {
  if (ms <= FIVE_MINUTES) return "Out soon";
  const compact = compactDuration(ms);
  return compact ? `Out in ~${compact}` : null;
}

function paceLeft(clock: Clock, start: number, end: number, now: number) {
  const total = clock(start, end);
  if (total <= 0) return null;
  return clamp((clock(Math.max(now, start), end) / total) * 100, 0, 100);
}

function project(
  used: number,
  window: UsageWindow,
  now: number,
  clock: Clock,
  learned: boolean,
): {
  status: "ahead" | "onTrack" | "behind";
  projected: number;
  eta: number | null;
} | null {
  const { resetsAt, periodMs } = window;
  if (resetsAt == null || used <= 0 || periodMs <= 0 || now >= resetsAt)
    return null;
  const start = resetsAt - periodMs;
  const elapsed = clock(start, now);
  // Learned hours already skip nights, so an hour of real work is enough.
  const minElapsed = learned ? HOUR : Math.max(60_000, periodMs * 0.01);
  if (elapsed < minElapsed) return null;
  const rate = used / elapsed;
  const projected = used + rate * clock(now, resetsAt);
  const status =
    used >= 100 || projected > 100
      ? "behind"
      : projected <= 90
        ? "ahead"
        : "onTrack";
  if (status !== "ahead" && used < 5) return null;
  if (status !== "behind") return { status, projected, eta: null };
  return {
    status,
    projected,
    eta: runsOutIn(used, rate, now, resetsAt, clock),
  };
}

/** Wall time until the limit is hit at this rate, if before the reset. */
function runsOutIn(
  used: number,
  rate: number,
  now: number,
  resetsAt: number,
  clock: Clock,
): number | null {
  const need = 100 - used;
  if (rate <= 0) return null;
  if (need <= 0) return 0;
  // Both clocks run evenly within an hour, so solve one hour at a time.
  let spent = 0;
  for (let t = now; t < resetsAt;) {
    const next = Math.min(resetsAt, nextHour(t));
    const burn = rate * clock(t, next);
    if (spent + burn >= need) {
      const at = t + ((need - spent) / burn) * (next - t);
      return Math.round((at - now) / 1000) * 1000;
    }
    spent += burn;
    t = next;
  }
  return null;
}

/** "62,500 credits", or "Unlimited credits". */
export function creditsLabel(credits: UsageCredits) {
  return credits.unlimited
    ? "Unlimited credits"
    : `${credits.balance.toLocaleString("en-US")} credits`;
}

/** "~15,625–81,250 messages", Codex's own estimate of what the credits buy. */
export function creditsReach(credits: UsageCredits) {
  if (credits.unlimited || !credits.messages) return null;
  const [low, high] = credits.messages.map((n) =>
    Math.round(n).toLocaleString("en-US"),
  );
  return low === high ? `~${low} messages` : `~${low}–${high} messages`;
}

const PACE_RANK: Record<MeterPace, number> = {
  ok: 0,
  warn: 1,
  hot: 2,
  spent: 3,
};

/** The model currently picked for a provider, used to scope independent pools. */
export type UsageModel = {
  id: string;
  name?: string;
};

/** Outer ("long") or inner ("session") ring slot — governs colour and order. */
export type DialRingRole = "long" | "session";

export type UsageDialRing<
  M extends Pick<
    UsageMeter,
    "id" | "kind" | "leftPercent" | "pace" | "model"
  > = UsageMeter,
> = {
  role: DialRingRole;
  meter: M;
};

export function worstPace(meters: Pick<UsageMeter, "pace">[]): MeterPace {
  return meters.reduce<MeterPace>(
    (worst, m) => (PACE_RANK[m.pace] > PACE_RANK[worst] ? m.pace : worst),
    "ok",
  );
}

function modelText(model?: UsageModel) {
  return [model?.id, model?.name].filter(Boolean).join(" ").trim().toLowerCase();
}

function matchesToken(text: string, token: string) {
  const escaped = token
    .trim()
    .toLowerCase()
    .replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  if (!escaped) return false;
  return new RegExp(`(^|[^a-z0-9])${escaped}([^a-z0-9]|$)`).test(text);
}

function antigravityPool(text: string): "gemini-" | "3p-" | null {
  if (/\bgemini\b/.test(text)) return "gemini-";
  if (/\b(claude|opus|sonnet|haiku|gpt|openai|o[1-9])\b/.test(text))
    return "3p-";
  return null;
}

function isCursorIncludedModel(model: UsageModel, text: string) {
  // Cursor's Auto runs under id "default" or "auto"; Composer shares the Total-only pool.
  return (
    model.id.trim().toLowerCase() === "default" ||
    /\b(auto|composer)\b/.test(text)
  );
}

/**
 * The meters that actually cap the picked model. Without a model (such as
 * Accounts settings), keeps every plan-level pool and drops model-only limits.
 */
export function metersForModel<
  M extends Pick<UsageMeter, "id" | "kind" | "model">,
>(provider: UsageProvider, meters: M[], model?: UsageModel): M[] {
  const plan = meters.filter((m) => !m.model);
  const text = modelText(model);
  if (!model || !text) return plan;

  if (provider === "antigravity") {
    const prefix = antigravityPool(text);
    if (!prefix) return plan;
    const pool = plan.filter((m) => m.id?.startsWith(prefix));
    return pool.length ? pool : plan;
  }

  if (provider === "cursor") {
    if (!isCursorIncludedModel(model, text)) return plan;
    const total = plan.filter((m) => m.id === "total");
    return total.length ? total : plan;
  }

  if (provider === "claude") {
    return meters.filter((m) => !m.model || matchesToken(text, m.model));
  }

  return plan;
}

function tightest<M extends Pick<UsageMeter, "leftPercent" | "pace">>(
  meters: M[],
): M | undefined {
  return meters.reduce<M | undefined>((worst, m) => {
    if (!worst) return m;
    if (m.leftPercent !== worst.leftPercent)
      return m.leftPercent < worst.leftPercent ? m : worst;
    return PACE_RANK[m.pace] > PACE_RANK[worst.pace] ? m : worst;
  }, undefined);
}

/**
 * The 1 or 2 concentric rings to draw for active meters, outermost first.
 * Drops empty slots instead of drawing a 0% track, and splits paired monthly
 * pools (Cursor's Total + API) across both rings.
 */
export function dialRings<
  M extends Pick<UsageMeter, "id" | "kind" | "leftPercent" | "pace" | "model">,
>(meters: M[]): UsageDialRing<M>[] {
  const longs = meters.filter(
    (m) => m.kind === "weekly" || m.kind === "monthly",
  );
  const sessions = meters.filter((m) => m.kind === "session");
  const long = tightest(longs);
  const session = tightest(sessions);
  if (long && session) {
    return [
      { role: "long", meter: long },
      { role: "session", meter: session },
    ];
  }
  if (longs.length >= 2) {
    const total = longs.find((m) => m.id === "total");
    const sub = total
      ? tightest(longs.filter((m) => m !== total))
      : undefined;
    if (total && sub) {
      return [
        { role: "long", meter: total },
        { role: "session", meter: sub },
      ];
    }
  }
  if (long) return [{ role: "long", meter: long }];
  if (session) return [{ role: "session", meter: session }];
  return [];
}

