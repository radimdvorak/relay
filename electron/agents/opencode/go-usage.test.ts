import { homedir } from "node:os";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { providerUsageSchema } from "../../../shared/provider-usage";

const sdk = vi.hoisted(() => ({ discover: vi.fn() }));

vi.mock("@opencode/client/service", () => ({ discover: sdk.discover }));

const { monthBefore, openCodeGoWindows, readOpenCodeUsage } =
  await import("./go-usage");

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const DATE_LIMIT = 8.64e15;

// The shape OpenCode 2.3.2's service answers with; the numbers are made up.
const go = {
  rolling: {
    percent: 30,
    limited: false,
    resetsAt: Date.UTC(2026, 10, 22, 15),
  },
  weekly: { percent: 62, limited: false, resetsAt: Date.UTC(2026, 10, 23) },
  monthly: {
    percent: 49,
    limited: false,
    resetsAt: Date.UTC(2026, 11, 17, 21, 25, 51),
  },
};

const workspace = {
  id: "wrk_1",
  name: "Sample",
  usageURL: "https://example.test/wrk_1/activity/overview",
};

const signedIn = (extra: object = { go }) => ({
  location: { directory: "/home" },
  data: { workspace, budgets: [], ...extra },
});

const service = {
  url: "http://127.0.0.1:4096",
  auth: { type: "basic", username: "opencode", password: "pw" },
};

const ask = vi.fn();

/** The service answers the limits request with this. */
function answers(body: unknown, status = 200) {
  ask.mockImplementation(
    async () =>
      new Response(typeof body === "string" ? body : JSON.stringify(body), {
        status,
      }),
  );
}

beforeEach(() => {
  ask.mockReset();
  sdk.discover.mockReset().mockResolvedValue(service);
  vi.stubGlobal("fetch", ask);
});

afterEach(() => vi.unstubAllGlobals());

describe("openCodeGoWindows", () => {
  it("makes a window of each limit, resetting when OpenCode says", () => {
    expect(openCodeGoWindows(go)).toEqual([
      {
        kind: "session",
        usedPercent: 30,
        resetsAt: go.rolling.resetsAt,
        periodMs: 5 * HOUR,
      },
      {
        kind: "weekly",
        usedPercent: 62,
        resetsAt: go.weekly.resetsAt,
        periodMs: 7 * DAY,
      },
      {
        kind: "monthly",
        usedPercent: 49,
        resetsAt: go.monthly.resetsAt,
        // 17 November to 17 December, if the month began on the 17th.
        periodMs: 30 * DAY,
        periodEstimated: true,
      },
    ]);
  });

  it("calls only the month's length a guess", () => {
    const [session, weekly] = openCodeGoWindows(go);
    expect(session.periodEstimated).toBeUndefined();
    expect(weekly.periodEstimated).toBeUndefined();
  });

  it("shows a limit Go refuses requests for as used up", () => {
    const [session, weekly] = openCodeGoWindows({
      ...go,
      rolling: { ...go.rolling, percent: 97, limited: true },
      weekly: { ...go.weekly, percent: 140 },
    });
    expect(session.usedPercent).toBe(100);
    // A reading past the limit is held to it, which the usage schema requires.
    expect(weekly.usedPercent).toBe(100);
  });
});

describe("monthBefore", () => {
  const at = (year: number, month: number, day: number) =>
    Date.UTC(year, month - 1, day, 21, 25, 51);

  it("is the same moment a month earlier", () => {
    expect(monthBefore(at(2026, 12, 17))).toBe(at(2026, 11, 17));
    expect(monthBefore(at(2026, 3, 1))).toBe(at(2026, 2, 1));
  });

  it("crosses the new year", () => {
    expect(monthBefore(at(2027, 1, 31))).toBe(at(2026, 12, 31));
  });

  it("keeps to the shorter month before a day it doesn't have", () => {
    expect(monthBefore(at(2026, 3, 31))).toBe(at(2026, 2, 28));
    expect(monthBefore(at(2028, 3, 30))).toBe(at(2028, 2, 29));
    expect(monthBefore(at(2026, 5, 31))).toBe(at(2026, 4, 30));
  });

  it("measures the month before the reset, whatever its length", () => {
    const [, , monthly] = openCodeGoWindows({
      ...go,
      monthly: { ...go.monthly, resetsAt: at(2026, 3, 1) },
    });
    // February 2026.
    expect(monthly.periodMs).toBe(28 * DAY);
  });

  it("has no answer past what a Date holds", () => {
    expect(monthBefore(-DATE_LIMIT)).toBeNaN();
  });
});

describe("readOpenCodeUsage", () => {
  it("asks the discovered service, with the password it was found with", async () => {
    answers(signedIn());
    const usage = await readOpenCodeUsage();
    const [url, init] = ask.mock.calls[0] as [URL, RequestInit];
    expect(url.origin).toBe("http://127.0.0.1:4096");
    expect(url.pathname).toBe("/api/experimental/usage/limits");
    expect(url.searchParams.get("location[directory]")).toBe(homedir());
    expect(init.headers).toEqual({
      accept: "application/json",
      authorization: `Basic ${Buffer.from("opencode:pw").toString("base64")}`,
    });
    expect(init.redirect).toBe("manual");
    expect(usage.message).toBeNull();
    expect(usage.windows.map((window) => window.kind)).toEqual([
      "session",
      "weekly",
      "monthly",
    ]);
  });

  it("sends no password the service didn't register", async () => {
    sdk.discover.mockResolvedValue({ url: service.url, auth: undefined });
    answers(signedIn());
    await readOpenCodeUsage();
    const [, init] = ask.mock.calls[0] as [URL, RequestInit];
    expect(init.headers).toEqual({ accept: "application/json" });
  });

  it("hands back nothing of the workspace", async () => {
    answers(signedIn());
    expect(JSON.stringify(await readOpenCodeUsage())).not.toMatch(
      /wrk_1|Sample|example\.test/,
    );
  });

  it("gives a reading the usage schema accepts", async () => {
    answers(signedIn({ go: { ...go, weekly: { ...go.weekly, percent: -3 } } }));
    const usage = await readOpenCodeUsage();
    expect(() => providerUsageSchema.parse(usage)).not.toThrow();
  });

  it("waits for OpenCode when no service is registered or answering", async () => {
    sdk.discover.mockResolvedValue(undefined);
    expect(await readOpenCodeUsage()).toEqual({
      provider: "opencode",
      windows: [],
      message: "Shows while OpenCode is running",
    });
    // Nothing is asked, so nothing is started.
    expect(ask).not.toHaveBeenCalled();
  });

  it("asks for a Console workspace sign-in when there's none to read with", async () => {
    answers({ location: { directory: "/home" }, data: null });
    expect(await readOpenCodeUsage()).toEqual({
      provider: "opencode",
      windows: [],
      message: "Go usage needs a Console workspace sign-in",
    });
  });

  it("doesn't claim a missing plan when Go just reports no limits", async () => {
    answers(signedIn({}));
    expect(await readOpenCodeUsage()).toEqual({
      provider: "opencode",
      windows: [],
      message: "No Go limits reported for this workspace",
    });
  });

  it("says when OpenCode is too old to have the route", async () => {
    answers({ message: "Not found" }, 404);
    expect((await readOpenCodeUsage()).message).toBe(
      "This OpenCode doesn't report Go limits",
    );
  });

  it("doesn't take an empty or partial answer for a state", async () => {
    for (const body of [
      "",
      "not json",
      "null",
      {},
      { data: {} },
      { data: { go } },
      { data: { workspace: {}, budgets: [], go } },
      { data: { workspace, go } },
      { data: { workspace, budgets: [], go: { rolling: "30%" } } },
      { data: { workspace, budgets: [], go: { ...go, weekly: undefined } } },
    ]) {
      answers(body);
      expect(await readOpenCodeUsage()).toEqual({
        provider: "opencode",
        windows: [],
        message: "Couldn't read usage",
      });
    }
  });

  it("is unavailable when the service refuses or fails", async () => {
    for (const status of [401, 403, 500, 503]) {
      answers({ error: "nope" }, status);
      await expect(readOpenCodeUsage()).rejects.toThrow(String(status));
    }
  });

  it("isn't taken in by a reset a Date can't hold", async () => {
    const reading = (resetsAt: unknown) => {
      answers(
        signedIn({
          go: { ...go, monthly: { ...go.monthly, resetsAt } },
        }),
      );
      return readOpenCodeUsage();
    };
    for (const resetsAt of [
      1e20,
      DATE_LIMIT + 1,
      -DATE_LIMIT,
      -1e20,
      1.5,
      "1796000000000",
      null,
    ]) {
      expect((await reading(resetsAt)).message).toBe("Couldn't read usage");
    }
    // The furthest a Date goes is still a reading, with a length it can pace.
    const edge = await reading(DATE_LIMIT);
    expect(edge.message).toBeNull();
    expect(Number.isFinite(edge.windows[2].periodMs)).toBe(true);
    expect(() => providerUsageSchema.parse(edge)).not.toThrow();
  });

  it("won't send the password anywhere but a local HTTP service", async () => {
    for (const url of [
      "https://127.0.0.1:4096",
      "http://example.com:4096",
      "http://user:secret@127.0.0.1:4096",
      "not a url",
    ]) {
      sdk.discover.mockResolvedValue({ ...service, url });
      await expect(readOpenCodeUsage()).rejects.toThrow(
        "OpenCode didn't report",
      );
    }
    expect(ask).not.toHaveBeenCalled();
  });
});
