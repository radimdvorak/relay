import { createServer, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { homedir, tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { usageProviders } from "../../shared/agents";
import {
  providerUsageSchema,
  type ProviderUsage,
} from "../../shared/provider-usage";
import type { ProjectChat } from "../../shared/projects";
import { usageLimits } from "../started-threads/usage-limits";
import { readProviderUsage } from "./provider-usage";

// The whole way a reading takes: the API method, the cache, the reader, the
// SDK's discover() and a real HTTP service on loopback, standing in for
// OpenCode 2 with a registration file in a folder of its own. No key is used.
const { settingsHandlers } = await import("../api/settings");

const PASSWORD = "test-password";
const HOUR = 60 * 60 * 1000;

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

const workspace = { id: "wrk_1", name: "Sample", usageURL: "https://x.test/" };

const signedIn = (extra: object = { go }) => ({
  location: { directory: "/home" },
  data: { workspace, budgets: [], ...extra },
});

/** What the fake service does when asked for the limits. */
const behavior = vi.hoisted(() => ({
  status: 200,
  body: undefined as unknown,
}));

const asked = {
  limits: [] as { authorization?: string; directory: string | null }[],
  info: 0,
};

let state: string;
let service: Server | undefined;
let counter = 0;

const start = () =>
  new Promise<string>((resolve) => {
    service = createServer((req, res) => {
      const url = new URL(req.url ?? "/", "http://service.test");
      const expected = `Basic ${Buffer.from(`opencode:${PASSWORD}`).toString("base64")}`;
      if (req.headers.authorization !== expected) {
        res.statusCode = 401;
        return void res.end("{}");
      }
      if (url.pathname === "/api/info") {
        asked.info++;
        return void res.end(
          JSON.stringify({ version: "2.3.2", pid: process.pid }),
        );
      }
      if (url.pathname === "/api/experimental/usage/limits") {
        asked.limits.push({
          authorization: req.headers.authorization,
          directory: url.searchParams.get("location[directory]"),
        });
        res.statusCode = behavior.status;
        const { body } = behavior;
        return void res.end(
          typeof body === "string" ? body : JSON.stringify(body),
        );
      }
      res.statusCode = 404;
      res.end();
    });
    service.listen(0, "127.0.0.1", () =>
      resolve(`http://127.0.0.1:${(service!.address() as AddressInfo).port}`),
    );
  });

const stop = () =>
  new Promise<void>((resolve) => {
    if (!service) return resolve();
    service.closeAllConnections();
    service.close(() => resolve());
    service = undefined;
  });

/** OpenCode's registration file, where the SDK looks for a running service. */
async function register(url: string) {
  await mkdir(join(state, "opencode"), { recursive: true });
  await writeFile(
    join(state, "opencode", "service.json"),
    JSON.stringify({
      id: "service",
      version: "2.3.2",
      url,
      pid: process.pid,
      password: PASSWORD,
    }),
  );
}

/** Each test reads its own account, so the module's cache starts empty. */
const read = (account: string, force = false) =>
  readProviderUsage("opencode", force, account);

const account = () => `opencode-test-${++counter}`;

beforeEach(async () => {
  state = await mkdtemp(join(tmpdir(), "opencode-state-"));
  vi.stubEnv("XDG_STATE_HOME", state);
  behavior.status = 200;
  behavior.body = signedIn();
  asked.limits = [];
  asked.info = 0;
});

afterEach(async () => {
  vi.useRealTimers();
  vi.unstubAllEnvs();
  await stop();
  await rm(state, { recursive: true, force: true });
});

const unreadable: ProviderUsage = {
  provider: "opencode",
  windows: [],
  message: "Couldn't read usage",
};

describe("providerUsage for OpenCode", () => {
  it("reads Go's limits from the running service through the API method", async () => {
    await register(await start());
    const api = settingsHandlers({} as never);
    const usage = (await api.providerUsage([
      "opencode",
      true,
    ])) as ProviderUsage;
    expect(usage.message).toBeNull();
    expect(usage.windows.map((w) => [w.kind, w.usedPercent])).toEqual([
      ["session", 30],
      ["weekly", 62],
      ["monthly", 49],
    ]);
    expect(usage.windows[2].periodEstimated).toBe(true);
    expect(() => providerUsageSchema.parse(usage)).not.toThrow();
    // The service's own password, for the folder Relay asks about.
    expect(asked.limits).toEqual([
      {
        authorization: expect.stringMatching(/^Basic /),
        directory: homedir(),
      },
    ]);
    expect(JSON.stringify(usage)).not.toMatch(/wrk_1|Sample|test-password/);
  });

  it("refuses an agent or account the API doesn't know", () => {
    const api = settingsHandlers({} as never);
    expect(() => api.providerUsage(["not-an-agent"])).toThrow();
  });
});

describe("when OpenCode isn't running", () => {
  it("asks nothing and starts nothing without a registered service", async () => {
    const url = await start();
    // A service is up, but nothing registers it: it isn't OpenCode's to find.
    expect(url).toMatch(/^http:/);
    const usage = await read(account(), true);
    expect(usage).toMatchObject({
      provider: "opencode",
      windows: [],
      message: "Shows while OpenCode is running",
    });
    expect(asked).toEqual({ limits: [], info: 0 });
  });

  it("treats a registration the service has left behind as stopped", async () => {
    await register(await start());
    await stop();
    const usage = await read(account(), true);
    expect(usage.message).toBe("Shows while OpenCode is running");
    expect(usage.windows).toEqual([]);
  });

  it("looks again once the empty reading has lapsed, and not before", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(Date.UTC(2026, 10, 22, 12));
    const id = account();
    expect((await read(id)).message).toBe("Shows while OpenCode is running");
    // OpenCode starts; the empty reading holds for a few seconds.
    await register(await start());
    vi.setSystemTime(Date.now() + 7_000);
    expect((await read(id)).windows).toEqual([]);
    expect(asked.limits).toHaveLength(0);
    vi.setSystemTime(Date.now() + 2_000);
    expect((await read(id)).windows).toHaveLength(3);
    expect(asked.limits).toHaveLength(1);
  });

  it("goes back to waiting when the service stops after a reading", async () => {
    await register(await start());
    const id = account();
    expect((await read(id, true)).windows).toHaveLength(3);
    await stop();
    const after = await read(id, true);
    expect(after.windows).toEqual([]);
    expect(after.message).toBe("Shows while OpenCode is running");
  });
});

describe("caching and concurrent readings", () => {
  it("shares one request among readings that overlap", async () => {
    await register(await start());
    const id = account();
    const readings = await Promise.all(
      Array.from({ length: 6 }, () => read(id, true)),
    );
    expect(asked.limits).toHaveLength(1);
    expect(new Set(readings.map((r) => JSON.stringify(r))).size).toBe(1);
    expect(readings[0].windows).toHaveLength(3);
  });

  it("holds a reading with limits for 45 seconds, and a forced one skips that", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(Date.UTC(2026, 10, 22, 12));
    await register(await start());
    const id = account();
    await read(id);
    vi.setSystemTime(Date.now() + 44_000);
    await read(id);
    expect(asked.limits).toHaveLength(1);
    await read(id, true);
    expect(asked.limits).toHaveLength(2);
    vi.setSystemTime(Date.now() + 46_000);
    await read(id);
    expect(asked.limits).toHaveLength(3);
  });

  it("keeps readings of different accounts apart", async () => {
    await register(await start());
    await Promise.all([read(account(), true), read(account(), true)]);
    expect(asked.limits).toHaveLength(2);
  });

  it("recovers on the next reading after a bad answer", async () => {
    await register(await start());
    const id = account();
    behavior.status = 500;
    expect(await read(id, true)).toMatchObject(unreadable);
    behavior.status = 200;
    expect((await read(id, true)).windows).toHaveLength(3);
  });
});

describe("answers that aren't Go's limits", () => {
  const bad: [string, number, unknown][] = [
    ["an empty body", 200, ""],
    ["text", 200, "not json"],
    ["null", 200, "null"],
    ["an empty object", 200, {}],
    ["an empty envelope", 200, { data: {} }],
    ["limits without their workspace", 200, { data: { go } }],
    [
      "a reset beyond what a Date holds",
      200,
      signedIn({ go: { ...go, monthly: { ...go.monthly, resetsAt: 1e20 } } }),
    ],
    ["a refusal", 401, { error: "nope" }],
    ["a server error", 500, { error: "boom" }],
    ["an unavailable Console", 503, { error: "Console is down" }],
  ];

  it.each(bad)("is couldn't-read for %s", async (_name, status, body) => {
    await register(await start());
    behavior.status = status;
    behavior.body = body;
    expect(await read(account(), true)).toMatchObject(unreadable);
  });

  it("names each state it can prove, and no other", async () => {
    await register(await start());
    const message = async (status: number, body: unknown) => {
      behavior.status = status;
      behavior.body = body;
      return (await read(account(), true)).message;
    };
    expect(await message(200, { location: {}, data: null })).toBe(
      "Go usage needs a Console workspace sign-in",
    );
    expect(await message(200, signedIn({}))).toBe(
      "No Go limits reported for this workspace",
    );
    expect(await message(404, { message: "Not found" })).toBe(
      "This OpenCode doesn't report Go limits",
    );
  });

  it("leaves the other agents' limits whole when OpenCode's answer is bad", async () => {
    await register(await start());
    behavior.body = { data: {} };
    const claude: ProviderUsage = {
      provider: "claude",
      message: null,
      windows: [
        {
          kind: "session",
          usedPercent: 25,
          resetsAt: Date.now() + HOUR,
          periodMs: 5 * HOUR,
        },
      ],
    };
    const reads: string[] = [];
    const answers = await usageLimits({} as ProjectChat, (provider, force) => {
      reads.push(provider);
      if (provider === "opencode")
        return readProviderUsage(provider, force, account());
      return Promise.resolve(
        provider === "claude"
          ? claude
          : { provider, windows: [], message: `Sign in with ${provider}` },
      );
    });
    expect(reads).toEqual([...usageProviders]);
    expect(answers.map((a) => a.agent)).toEqual([...usageProviders]);
    expect(answers.find((a) => a.agent === "opencode")).toEqual({
      agent: "opencode",
      note: "Couldn't read usage",
    });
    expect(answers.find((a) => a.agent === "claude")).toMatchObject({
      session: { usedPercent: 25 },
    });
  });
});
