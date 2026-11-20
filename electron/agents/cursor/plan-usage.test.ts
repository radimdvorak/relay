import { mkdtempSync, mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const home = vi.hoisted(() => ({ dir: "" }));

vi.mock("node:os", async (actual) => ({
  ...(await actual<typeof import("node:os")>()),
  homedir: () => home.dir,
}));

const { cursorWindows, readCursorUsage } = await import("./plan-usage");

// Trimmed from a real GetCurrentPeriodUsage answer on a Pro plan.
const periodUsage = {
  billingCycleStart: "1794301767000",
  billingCycleEnd: "1796893767000",
  planUsage: {
    totalSpend: 536,
    limit: 2000,
    autoPercentUsed: 0.015555555555555557,
    apiPercentUsed: 23.51111111111111,
    totalPercentUsed: 1.1343915343915343,
  },
  displayMessage: "You've used 27% of your included usage",
};

function signIn() {
  mkdirSync(join(home.dir, ".cursor", "sdk"), { recursive: true });
  writeFileSync(
    join(home.dir, ".cursor", "sdk", "auth.json"),
    JSON.stringify({
      version: 1,
      backendUrl: "https://api2.cursor.sh",
      apiKey: "key_saved",
      createdAtMs: 1,
    }),
  );
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status });

beforeEach(() => {
  home.dir = mkdtempSync(join(tmpdir(), "cursor-usage-"));
});

afterEach(() => vi.unstubAllGlobals());

describe("cursorWindows", () => {
  it("makes a monthly window of each pool, resetting with the billing cycle", () => {
    expect(cursorWindows(periodUsage)).toEqual([
      {
        id: "api",
        label: "API",
        kind: "monthly",
        usedPercent: 23.51111111111111,
        resetsAt: 1796893767000,
        periodMs: 2592000000,
      },
      {
        id: "total",
        label: "Total",
        kind: "monthly",
        usedPercent: 1.1343915343915343,
        resetsAt: 1796893767000,
        periodMs: 2592000000,
      },
    ]);
  });

  it("has nothing to show without a billing cycle", () => {
    expect(cursorWindows({ planUsage: { apiPercentUsed: 5 } })).toEqual([]);
  });
});

describe("readCursorUsage", () => {
  it("asks for a sign-in without calling Cursor when the SDK saved none", async () => {
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    expect(await readCursorUsage()).toMatchObject({
      windows: [],
      message: "Sign in with Cursor",
    });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("trades the saved key for a token and reads the period with it", async () => {
    signIn();
    const fetch = vi.fn(async (url: string, _init: RequestInit) =>
      url.endsWith("/auth/exchange_user_api_key")
        ? json(200, { accessToken: "token_fresh", refreshToken: "r" })
        : json(200, periodUsage),
    );
    vi.stubGlobal("fetch", fetch);
    const usage = await readCursorUsage();
    expect(usage.windows.map((w) => w.id)).toEqual(["api", "total"]);
    expect(usage.message).toBeNull();
    const sent = fetch.mock.calls.map(([url, init]) => ({
      url,
      headers: init.headers as Record<string, string>,
    }));
    expect(sent[0].headers.authorization).toBe("Bearer key_saved");
    expect(sent[1].url).toContain("DashboardService/GetCurrentPeriodUsage");
    expect(sent[1].headers.authorization).toBe("Bearer token_fresh");
  });

  it("asks to sign in again when Cursor turns the saved key down", async () => {
    signIn();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => json(401, { error: "nope" })),
    );
    expect(await readCursorUsage()).toMatchObject({
      windows: [],
      message: "Sign in with Cursor again",
    });
  });

  it("fails, rather than calling it a sign-out, when Cursor is down", async () => {
    signIn();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => json(503, {})),
    );
    await expect(readCursorUsage()).rejects.toThrow("503");
  });
});
