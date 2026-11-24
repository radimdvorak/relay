import { describe, expect, it } from "vitest";
import {
  SESSION_MS,
  WEEK_MS,
  creditsLabel,
  creditsReach,
  dialRings,
  metersForModel,
  paceGap,
  presentWindow,
  type UsageMeter,
} from "./provider-usage";
import { mapCodexCredits } from "./provider-usage-responses";

const now = Date.parse("2026-09-22T12:00:00Z");
const DAY = 24 * 60 * 60 * 1000;

describe("provider usage windows", () => {
  it("shows a reached limit, a burn-rate warning, and a session that has not started", () => {
    const spent = presentWindow(
      {
        kind: "session",
        usedPercent: 100,
        resetsAt: now + 85 * 60 * 1000,
        periodMs: SESSION_MS,
      },
      now,
    );
    expect(spent).toMatchObject({
      leftPercent: 0,
      pace: "spent",
      limitLabel: "Limit reached",
      resetLabel: "Resets in 1h 25m",
    });

    const weekly = presentWindow(
      {
        kind: "weekly",
        usedPercent: 16,
        resetsAt: now + (6 * 24 + 20) * 60 * 60 * 1000,
        periodMs: WEEK_MS,
      },
      now,
    );
    expect(weekly).toMatchObject({
      leftPercent: 84,
      // 164h of the 168h week still to go.
      paceLeftPercent: expect.closeTo(97.62, 2),
      pace: "hot",
      limitLabel: "Out in ~21h",
      resetLabel: "Resets in 6d 20h",
    });

    expect(
      presentWindow(
        {
          kind: "session",
          usedPercent: 0,
          resetsAt: null,
          periodMs: SESSION_MS,
        },
        now,
      ),
    ).toMatchObject({
      leftPercent: 100,
      pace: "ok",
      limitLabel: null,
      resetLabel: "Not started",
    });
  });

  it("says how far the meter is from its pace mark", () => {
    // Half the week gone: an even burn leaves 50%.
    const week = (usedPercent: number) =>
      presentWindow(
        {
          kind: "weekly",
          usedPercent,
          resetsAt: now + WEEK_MS / 2,
          periodMs: WEEK_MS,
        },
        now,
      );
    expect(paceGap(week(45))).toBe("5% ahead");
    expect(paceGap(week(53))).toBe("3% behind");
    expect(paceGap(week(50))).toBe("On pace");
    expect(paceGap(week(100))).toBeNull();
  });

  it("calls the pace a guess when the window's start isn't known", () => {
    const month = (usedPercent: number, periodEstimated?: boolean) =>
      presentWindow(
        {
          kind: "monthly",
          usedPercent,
          resetsAt: now + 15 * DAY,
          periodMs: 30 * DAY,
          periodEstimated,
        },
        now,
      );
    expect(month(45, true).paceEstimated).toBe(true);
    expect(paceGap(month(45, true))).toBe("Est. 5% ahead");
    expect(paceGap(month(53, true))).toBe("Est. 3% behind");
    expect(paceGap(month(50, true))).toBe("Est. on pace");
    // A known period keeps the plain wording.
    expect(month(45).paceEstimated).toBeUndefined();
    expect(paceGap(month(45))).toBe("5% ahead");
    // Nothing to compare once the limit is reached.
    expect(paceGap(month(100, true))).toBeNull();
  });

  it("keeps a meter a point either side of its pace mark calm", () => {
    // 4% of the week to go: an even burn leaves 4%.
    const week = (usedPercent: number) =>
      presentWindow(
        {
          kind: "weekly",
          usedPercent,
          resetsAt: now + WEEK_MS * 0.04,
          periodMs: WEEK_MS,
        },
        now,
      ).pace;
    expect(week(95)).toBe("ok");
    expect(week(97)).toBe("ok");
    expect(week(98)).not.toBe("ok");
  });

  it("keeps a meter calm when it will end the week with a little left", () => {
    // Half the week gone at 46%: this rate ends the week at 92%.
    const meter = presentWindow(
      {
        kind: "weekly",
        usedPercent: 46,
        resetsAt: now + WEEK_MS / 2,
        periodMs: WEEK_MS,
      },
      now,
    );
    expect(meter).toMatchObject({ pace: "ok", limitLabel: "~8% spare" });
  });
});

describe("Codex credits", () => {
  // As wham/usage answered for a Free account with bought credits.
  const body = (credits: Record<string, unknown>) => ({
    plan_type: "free",
    credits: {
      has_credits: true,
      unlimited: false,
      overage_limit_reached: false,
      balance: "62500",
      approx_local_messages: [15625, 81250],
      ...credits,
    },
  });

  it("reads the balance and what it buys", () => {
    const credits = mapCodexCredits(body({}))!;
    expect(credits).toEqual({
      balance: 62500,
      unlimited: false,
      messages: [15625, 81250],
    });
    expect(creditsLabel(credits)).toBe("62,500 credits");
    expect(creditsReach(credits)).toBe("~15,625–81,250 messages");
  });

  it("has nothing to show for an empty or capped balance", () => {
    expect(
      mapCodexCredits(body({ has_credits: false, balance: "0" })),
    ).toBeNull();
    expect(mapCodexCredits(body({ overage_limit_reached: true }))).toBeNull();
    expect(mapCodexCredits({ rate_limit: {} })).toBeNull();
  });

  it("keeps unlimited credits without a balance", () => {
    const credits = mapCodexCredits(
      body({ unlimited: true, balance: null, approx_local_messages: null }),
    )!;
    expect(creditsLabel(credits)).toBe("Unlimited credits");
    expect(creditsReach(credits)).toBeNull();
  });
});

describe("dial ring selection", () => {
  const meter = (
    overrides: Partial<UsageMeter> & Pick<UsageMeter, "kind" | "leftPercent">,
  ): UsageMeter => ({
    label: overrides.kind,
    paceLeftPercent: null,
    pace: "ok",
    limitLabel: null,
    resetLabel: null,
    ...overrides,
  });

  it("picks only the matching Antigravity pool and drops empty ring slots", () => {
    const all = [
      meter({ id: "gemini-models-5h", kind: "session", leftPercent: 97 }),
      meter({ id: "3p-weekly", kind: "weekly", leftPercent: 100 }),
      meter({ id: "3p-5h", kind: "session", leftPercent: 100 }),
    ];

    const gemini = metersForModel("antigravity", all, {
      id: "gemini-3-flash",
      name: "Gemini 3 Flash",
    });
    expect(dialRings(gemini).map((r) => [r.role, r.meter.id])).toEqual([
      ["session", "gemini-models-5h"],
    ]);

    const gpt = metersForModel("antigravity", all, {
      id: "gpt-oss-120b",
      name: "GPT-OSS 120B",
    });
    expect(dialRings(gpt).map((r) => [r.role, r.meter.id])).toEqual([
      ["long", "3p-weekly"],
      ["session", "3p-5h"],
    ]);

    // Without a model (e.g. Accounts settings), falls back to worst across pools.
    const unscoped = metersForModel("antigravity", all);
    expect(dialRings(unscoped).map((r) => [r.role, r.meter.id])).toEqual([
      ["long", "3p-weekly"],
      ["session", "gemini-models-5h"],
    ]);
  });

  it("draws Cursor Total alone for Auto/Composer and Total + API for named models", () => {
    const all = [
      meter({ id: "api", kind: "monthly", leftPercent: 40 }),
      meter({ id: "total", kind: "monthly", leftPercent: 85 }),
    ];

    expect(
      dialRings(
        metersForModel("cursor", all, {
          id: "composer-2.5",
          name: "Composer 2.5",
        }),
      ).map((r) => [r.role, r.meter.id]),
    ).toEqual([["long", "total"]]);

    expect(
      dialRings(
        metersForModel("cursor", all, {
          id: "gpt-5",
          name: "GPT-5",
        }),
      ).map((r) => [r.role, r.meter.id]),
    ).toEqual([
      ["long", "total"],
      ["session", "api"],
    ]);
  });

  it("includes a Claude model-scoped weekly limit when that model is picked and keeps the tighter weekly", () => {
    const all = [
      meter({ kind: "session", leftPercent: 80 }),
      meter({ kind: "weekly", leftPercent: 60 }),
      meter({
        id: "model:Fable",
        model: "Fable",
        kind: "weekly",
        leftPercent: 15,
        pace: "hot",
      }),
    ];

    expect(
      dialRings(metersForModel("claude", all)).map((r) => [
        r.role,
        r.meter.leftPercent,
      ]),
    ).toEqual([
      ["long", 60],
      ["session", 80],
    ]);

    expect(
      dialRings(
        metersForModel("claude", all, {
          id: "claude-fable-5-1[1m]",
          name: "Fable 5.1",
        }),
      ).map((r) => [r.role, r.meter.leftPercent]),
    ).toEqual([
      ["long", 15],
      ["session", 80],
    ]);
  });

  it("returns one ring for session-only limits and none while loading", () => {
    expect(
      dialRings([meter({ kind: "session", leftPercent: 72 })]).map((r) => [
        r.role,
        r.meter.leftPercent,
      ]),
    ).toEqual([["session", 72]]);
    expect(dialRings([])).toEqual([]);
  });
});
