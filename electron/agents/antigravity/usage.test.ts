import { mkdir, mkdtemp, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, expect, it, vi } from "vitest";
import {
  createAntigravityUsageReader,
  mapAntigravityUsage,
  mapGeminiModelQuota,
  readQuotaCredential,
  type QuotaCredential,
} from "./usage";

const credential = (
  accessToken: string,
  expiresAt = Date.now() + 3_600_000,
): QuotaCredential => ({ fingerprint: accessToken, accessToken, expiresAt });

const bucket = (
  bucketId: string,
  window: string,
  remainingFraction: number,
  resetTime = "2026-10-22T12:00:00Z",
) => ({
  bucketId,
  window,
  remainingFraction,
  resetTime,
});

const summary = {
  groups: [
    {
      buckets: [
        bucket("gemini-5h", "5h", 0.7),
        bucket("gemini-weekly", "weekly", 0.4),
      ],
    },
    {
      buckets: [bucket("3p-5h", "5h", 0.9), bucket("3p-weekly", "weekly", 0.6)],
    },
  ],
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
const urlOf = (url: Parameters<typeof fetch>[0]) => String(url);

afterEach(() => vi.restoreAllMocks());

it("keeps the independent Gemini and Claude/GPT windows, and ignores disabled, malformed and unknown cadences", () => {
  const windows = mapAntigravityUsage(summary);
  expect(windows.map((w) => [w.id, w.kind, Math.round(w.usedPercent)])).toEqual(
    [
      ["gemini-5h", "session", 30],
      ["gemini-weekly", "weekly", 60],
      ["3p-5h", "session", 10],
      ["3p-weekly", "weekly", 40],
    ],
  );
  expect(windows[2]?.label).toBe("Claude & GPT · 5h");
  expect(
    mapAntigravityUsage({
      buckets: [
        { ...bucket("disabled", "5h", 0), disabled: true },
        bucket("unknown", "unknown", 0.3),
        { bucketId: "missing", window: "5h" },
        bucket("bad-date", "weekly", 2, "nonsense"),
      ],
    }),
  ).toEqual([
    expect.objectContaining({ id: "bad-date", usedPercent: 0, resetsAt: null }),
  ]);
});

it("reads the access token the CLI saved, from the profile's file", async () => {
  const home = await mkdtemp(join(tmpdir(), "relay-agy-usage-"));
  await mkdir(join(home, "antigravity-acp"));
  await writeFile(
    join(home, "antigravity-acp", "antigravity-oauth-token"),
    JSON.stringify({
      token: {
        access_token: "access",
        refresh_token: "refresh",
        expiry: "2026-11-24T16:00:00Z",
      },
    }),
  );
  expect(await readQuotaCredential(home, true)).toMatchObject({
    accessToken: "access",
    expiresAt: Date.parse("2026-11-24T16:00:00Z"),
  });
});

it("sends the CLI's own token, and rereads it once after Google refuses it", async () => {
  let saved = credential("first");
  const read = vi.fn(async () => saved);
  const sent: string[] = [];
  const fetcher = vi.fn<typeof fetch>(async (_url, init) => {
    const bearer = (init!.headers as Record<string, string>).Authorization;
    sent.push(bearer);
    if (bearer === "Bearer first") {
      saved = credential("renewed");
      return json({}, 401);
    }
    return json(summary);
  });
  const service = createAntigravityUsageReader(read, fetcher);
  expect((await service()).windows).toHaveLength(4);
  expect(read).toHaveBeenCalledTimes(2);
  expect(sent.slice(-2)).toEqual(["Bearer renewed", "Bearer renewed"]);
});

it.each([403, 429, 500])(
  "doesn't call it a sign-out after HTTP %i",
  async (status) => {
    const service = createAntigravityUsageReader(
      async () => credential("same"),
      vi.fn<typeof fetch>(async () => json({}, status)),
    );
    const failed = await service();
    expect(failed.windows).toEqual([]);
    expect(failed.message).not.toMatch(/sign in/i);
  },
);

it("bounds auth retries and picks up a removed or replaced login on the next operation", async () => {
  let saved: QuotaCredential | undefined = credential("old");
  const read = vi.fn(async () => saved);
  const fetcher = vi.fn<typeof fetch>(async (_url, init) =>
    (init!.headers as Record<string, string>).Authorization === "Bearer old"
      ? json({}, 401)
      : json(summary),
  );
  const service = createAntigravityUsageReader(read, fetcher);
  expect((await service()).message).toMatch(/sign in.*again/i);
  expect(fetcher).toHaveBeenCalledTimes(4);
  expect(read).toHaveBeenCalledTimes(2);
  saved = undefined;
  expect((await service()).windows).toEqual([]);
  saved = credential("replacement");
  expect((await service()).windows).toHaveLength(4);
});

it("asks for a turn to renew a token that ran out, without sending it to Google", async () => {
  const fetcher = vi.fn<typeof fetch>(async () => json(summary));
  const service = createAntigravityUsageReader(
    async () => credential("stale", Date.now() - 1000),
    fetcher,
  );
  expect((await service()).message).toMatch(/needs renewing/);
  expect(fetcher).not.toHaveBeenCalled();
});

it("reads Gemini's pool from the model list when the summary covers only Claude and GPT", async () => {
  const google = (fraction: number, resetTime?: string) => ({
    modelProvider: "MODEL_PROVIDER_GOOGLE",
    quotaInfo: { remainingFraction: fraction, resetTime },
  });
  const models = {
    models: {
      "gemini-3-flash": google(0.75, "2026-10-22T15:00:00Z"),
      "gemini-2.5-pro": google(0.75, "2026-10-22T15:00:00Z"),
      tab_flash_lite_preview: google(0.1),
      "claude-opus-5-5-low": {
        modelProvider: "MODEL_PROVIDER_ANTHROPIC",
        quotaInfo: {
          remainingFraction: 0.2,
          resetTime: "2026-10-22T15:00:00Z",
        },
      },
    },
  };
  const fetcher = vi.fn<typeof fetch>(async (url) => {
    if (urlOf(url).endsWith(":fetchAvailableModels")) return json(models);
    return json({ groups: [summary.groups[1]] });
  });
  const service = createAntigravityUsageReader(
    async () => credential("one"),
    fetcher,
  );
  expect(
    (await service()).windows.map((w) => [w.label, Math.round(w.usedPercent)]),
  ).toEqual([
    ["Gemini · 5h", 25],
    ["Claude & GPT · 5h", 10],
    ["Claude & GPT · Weekly", 40],
  ]);
  expect(
    mapGeminiModelQuota({ models: { a: google(1, "2026-10-22T15:00:00Z") } }),
  ).toEqual([expect.objectContaining({ usedPercent: 0, resetsAt: null })]);
});

it("sorts 5h before weekly, unwraps response envelopes, keeps separate Gemini groups distinct, and drops untouched rolling resets", () => {
  const windows = mapAntigravityUsage({
    response: {
      groups: [
        {
          displayName: "Gemini Models",
          buckets: [
            bucket("gemini-weekly", "weekly", 0.98, "2026-12-01T05:51:32Z"),
            bucket("gemini-5h", "5h", 0.92, "2026-11-24T10:51:32Z"),
          ],
        },
        {
          displayName: "Gemini 4 Argon",
          buckets: [
            bucket(
              "gemini-argon-wtu-weekly",
              "weekly",
              0.5,
              "2026-12-01T05:51:32Z",
            ),
          ],
        },
        {
          displayName: "Claude and GPT models",
          buckets: [
            bucket("3p-weekly", "weekly", 1, "2026-12-01T06:18:40Z"),
            bucket("3p-5h", "5h", 1, "2026-11-24T11:18:40Z"),
          ],
        },
      ],
    },
  });

  expect(
    windows.map((w) => [w.id, w.label, Math.round(w.usedPercent), w.resetsAt]),
  ).toEqual([
    ["gemini-5h", "Gemini · 5h", 8, Date.parse("2026-11-24T10:51:32Z")],
    ["gemini-weekly", "Gemini · Weekly", 2, Date.parse("2026-12-01T05:51:32Z")],
    [
      "gemini-argon-wtu-weekly",
      "Gemini 4 Argon · Weekly",
      50,
      Date.parse("2026-12-01T05:51:32Z"),
    ],
    ["3p-5h", "Claude & GPT · 5h", 0, null],
    ["3p-weekly", "Claude & GPT · Weekly", 0, null],
  ]);
});

it("identifies as a current antigravity hub client so Google includes the Gemini quota group", async () => {
  const agents: string[] = [];
  const fetcher = vi.fn<typeof fetch>(async (_url, init) => {
    agents.push((init!.headers as Record<string, string>)["User-Agent"]);
    return json(summary);
  });
  const service = createAntigravityUsageReader(
    async () => credential("one"),
    fetcher,
  );
  await service();
  expect(agents.length).toBeGreaterThan(0);
  for (const agent of agents) expect(agent).toMatch(/^antigravity\/hub\/2\./);
});
