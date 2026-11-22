import { homedir } from "node:os";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { providerUsageSchema } from "../../../shared/provider-usage";
import { NotInstalledError } from "../../platform/executables";

const service = vi.hoisted(() => ({ ask: vi.fn() }));

vi.mock("./native-api", async (actual) => ({
  ...(await actual<typeof import("./native-api")>()),
  openCodeNativeApi: service.ask,
}));

const { monthBefore, openCodeGoWindows, readOpenCodeUsage } =
  await import("./go-usage");
const { OpenCodeStoppedError } = await import("./native-api");

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

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

const answer = (data: unknown) => ({ location: { directory: "/home" }, data });

const signedIn = (limits: unknown) =>
  answer({
    workspace: {
      id: "wrk_1",
      name: "Sample",
      usageURL: "https://example.test",
    },
    budgets: [],
    ...(limits === undefined ? {} : { go: limits }),
  });

beforeEach(() => {
  service.ask.mockReset();
});

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
        // 17 November to 17 December.
        periodMs: 30 * DAY,
      },
    ]);
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

  it("measures the month Go bills, whatever its length", () => {
    const [, , monthly] = openCodeGoWindows({
      ...go,
      monthly: { ...go.monthly, resetsAt: at(2026, 3, 1) },
    });
    // February 2026.
    expect(monthly.periodMs).toBe(28 * DAY);
  });
});

describe("readOpenCodeUsage", () => {
  it("asks a running service, and only that, for the usual folder", async () => {
    service.ask.mockResolvedValue(signedIn(go));
    const usage = await readOpenCodeUsage();
    const [method, path, body, options] = service.ask.mock.calls[0];
    expect(method).toBe("GET");
    expect(body).toBeUndefined();
    expect(options).toEqual({ passive: true });
    expect(path).toBe(
      `/api/experimental/usage/limits?${new URLSearchParams({ "location[directory]": homedir() })}`,
    );
    expect(usage.message).toBeNull();
    expect(usage.windows.map((window) => window.kind)).toEqual([
      "session",
      "weekly",
      "monthly",
    ]);
  });

  it("hands back nothing of the workspace", async () => {
    service.ask.mockResolvedValue(signedIn(go));
    expect(JSON.stringify(await readOpenCodeUsage())).not.toMatch(
      /wrk_1|Sample|example\.test/,
    );
  });

  it("gives a reading the usage schema accepts", async () => {
    service.ask.mockResolvedValue(
      signedIn({ ...go, weekly: { ...go.weekly, percent: -3 } }),
    );
    const usage = await readOpenCodeUsage();
    expect(() => providerUsageSchema.parse(usage)).not.toThrow();
  });

  it("says so when the workspace has no Go plan", async () => {
    service.ask.mockResolvedValue(signedIn(undefined));
    expect(await readOpenCodeUsage()).toEqual({
      provider: "opencode",
      windows: [],
      message: "No OpenCode Go plan",
    });
  });

  it("asks to sign in when OpenCode isn't signed in to a workspace", async () => {
    service.ask.mockResolvedValue(answer(null));
    expect(await readOpenCodeUsage()).toEqual({
      provider: "opencode",
      windows: [],
      message: "Sign in with OpenCode",
    });
  });

  it("waits for OpenCode when its service isn't running", async () => {
    service.ask.mockRejectedValue(new OpenCodeStoppedError("stopped"));
    expect(await readOpenCodeUsage()).toEqual({
      provider: "opencode",
      windows: [],
      message: "Shows while OpenCode is running",
    });
  });

  it("says so when OpenCode isn't installed", async () => {
    service.ask.mockRejectedValue(new NotInstalledError("opencode"));
    expect((await readOpenCodeUsage()).message).toBe(
      "OpenCode isn't installed",
    );
  });

  it("couldn't read an answer it doesn't recognise", async () => {
    service.ask.mockResolvedValue(answer({ go: { rolling: "30%" } }));
    expect(await readOpenCodeUsage()).toEqual({
      provider: "opencode",
      windows: [],
      message: "Couldn't read usage",
    });
  });

  it("leaves other failures to the usual couldn't-read message", async () => {
    service.ask.mockRejectedValue(new Error("HTTP 404"));
    await expect(readOpenCodeUsage()).rejects.toThrow("HTTP 404");
  });
});
