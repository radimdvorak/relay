import { describe, expect, it, vi } from "vitest";
vi.mock("../../lib/api", () => ({ api: {} }));
import { activeUsageModel, ringState } from "./UsageRing";
import { SESSION_MS, WEEK_MS } from "../../../shared/provider-usage";

const now = Date.parse("2026-09-22T12:00:00Z");

describe("ringState", () => {
  it("presents both windows and takes the worst pace for the trigger", () => {
    const state = ringState(
      {
        provider: "claude",
        message: null,
        windows: [
          {
            kind: "session",
            usedPercent: 40,
            resetsAt: now + 2 * 60 * 60 * 1000,
            periodMs: SESSION_MS,
          },
          {
            kind: "weekly",
            usedPercent: 95,
            resetsAt: now + 5 * 24 * 60 * 60 * 1000,
            periodMs: WEEK_MS,
          },
        ],
      },
      now,
    );
    expect(state?.meters.map((m) => [m.kind, m.leftPercent])).toEqual([
      ["session", 60],
      ["weekly", 5],
    ]);
    expect(state?.pace).toBe("hot");
    expect(state?.label).toContain("Session 60% left");
    expect(state?.label).toContain("Weekly 5% left");
  });

  it("maps Cursor Total and API across both rings for named models, and Total alone for Auto", () => {
    const usage = {
      provider: "cursor" as const,
      message: null,
      windows: [
        {
          id: "api",
          label: "API",
          kind: "monthly" as const,
          usedPercent: 95,
          resetsAt: now + 20 * 24 * 60 * 60 * 1000,
          periodMs: 30 * 24 * 60 * 60 * 1000,
        },
        {
          id: "total",
          label: "Total",
          kind: "monthly" as const,
          usedPercent: 20,
          resetsAt: now + 20 * 24 * 60 * 60 * 1000,
          periodMs: 30 * 24 * 60 * 60 * 1000,
        },
      ],
    };

    const onAuto = ringState(usage, now, { id: "default", name: "Auto" })!;
    expect(onAuto.pace).toBe("ok");
    expect(onAuto.activeMeters.map((m) => m.id)).toEqual(["total"]);
    expect(onAuto.meters.map((m) => [m.id, m.active, m.ringRole])).toEqual([
      ["total", true, "long"],
      ["api", false, undefined],
    ]);

    const onNamed = ringState(usage, now, {
      id: "claude-4.5-sonnet",
      name: "Claude 4.5 Sonnet",
    })!;
    expect(onNamed.pace).toBe("hot");
    expect(onNamed.meters.map((m) => [m.id, m.active, m.ringRole])).toEqual([
      ["api", true, "session"],
      ["total", true, "long"],
    ]);
  });

  it("includes Claude's per-model weekly limit only when that model is picked", () => {
    const usage = {
      provider: "claude" as const,
      message: null,
      windows: [
        {
          kind: "session" as const,
          usedPercent: 10,
          resetsAt: now + 4 * 60 * 60 * 1000,
          periodMs: SESSION_MS,
        },
        {
          kind: "weekly" as const,
          usedPercent: 20,
          resetsAt: now + 5 * 24 * 60 * 60 * 1000,
          periodMs: WEEK_MS,
        },
        {
          id: "model:Fable",
          label: "Fable · Weekly",
          model: "Fable",
          kind: "weekly" as const,
          usedPercent: 100,
          resetsAt: now + 5 * 24 * 60 * 60 * 1000,
          periodMs: WEEK_MS,
        },
      ],
    };

    const onOpus = ringState(usage, now, {
      id: "opus claude-opus-4-6",
      name: "Opus 4.6",
    })!;
    expect(onOpus.pace).toBe("ok");
    expect(onOpus.meters.find((m) => m.id === "model:Fable")?.active).toBe(
      false,
    );

    const onFable = ringState(usage, now, {
      id: "fable claude-fable-5-2",
      name: "Fable 5.2",
    })!;
    expect(onFable.pace).toBe("spent");
    expect(onFable.meters.find((m) => m.id === "model:Fable")?.active).toBe(
      true,
    );
  });

  it("has nothing to draw before usage arrives or when no limits exist", () => {
    expect(ringState(undefined, now)).toBeNull();
    expect(
      ringState({ provider: "codex", windows: [], message: "Signed out" }, now),
    ).toBeNull();
  });
});

describe("activeUsageModel", () => {
  it("resolves picked models, catalog names, and Default fallbacks", () => {
    const catalogs = {
      of: (p: string) =>
        p === "opencode"
          ? [
              {
                id: "m18",
                name: "Gemini 3.1 Pro",
                description: "",
                efforts: [],
              },
            ]
          : p === "cursor"
            ? [{ id: "default", name: "Auto", description: "", efforts: [] }]
            : undefined,
      defaults: {
        of: (p: string) =>
          p === "cursor"
            ? { model: "default", effort: "" as const }
            : p === "claude"
              ? { model: "fable", effort: "" as const }
              : undefined,
        effort: () => "" as const,
        refresh: () => {},
      },
    };
    const runs = {
      choiceFor: (p: string) =>
        p === "opencode"
          ? { model: "m18", reasoningEffort: "" as const, fast: false }
          : { model: "", reasoningEffort: "" as const, fast: false },
      claude: { model: "" },
      claudeRuns: {
        id: "fable",
        name: "Fable 5.2",
        resolved: "claude-fable-5-2",
        description: "",
        efforts: [],
        longContext: false,
      },
    };

    expect(
      activeUsageModel("opencode", runs as never, catalogs as never),
    ).toEqual({ id: "m18", name: "Gemini 3.1 Pro" });
    expect(
      activeUsageModel("cursor", runs as never, catalogs as never),
    ).toEqual({ id: "default", name: "Auto" });
    expect(
      activeUsageModel("claude", runs as never, catalogs as never),
    ).toEqual({ id: "fable claude-fable-5-2", name: "Fable 5.2" });
  });
});
