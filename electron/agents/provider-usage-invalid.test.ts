import { expect, it, vi } from "vitest";
import { usageProviders } from "../../shared/agents";
import type { ProviderUsage } from "../../shared/provider-usage";
import type { ProjectChat } from "../../shared/projects";
import { usageLimits } from "../started-threads/usage-limits";

const reader = vi.hoisted(() => ({ reading: undefined as unknown }));
const recordUsage = vi.hoisted(() => vi.fn(async () => null));

vi.mock("./opencode/go-usage", () => ({
  readOpenCodeUsage: async () => reader.reading,
}));

vi.mock("./usage-history", () => ({ recordUsage }));

const { readProviderUsage } = await import("./provider-usage");

const window = (usedPercent: number, periodMs: number, resetsAt: number) => ({
  kind: "monthly" as const,
  usedPercent,
  resetsAt,
  periodMs,
});

it("turns a reading that isn't valid into a couldn't-read one, for that agent only", async () => {
  recordUsage.mockClear();
  for (const [account, invalid] of [
    ["nan-period", window(40, NaN, 0)],
    ["nan-reset", window(40, 1, NaN)],
    ["over", window(140, 1, 1)],
    ["negative-period", window(40, -5, 1)],
  ] as const) {
    reader.reading = {
      provider: "opencode",
      windows: [invalid],
      message: null,
    } satisfies ProviderUsage;
    await expect(
      readProviderUsage("opencode", true, account),
    ).resolves.toMatchObject({
      provider: "opencode",
      windows: [],
      message: "Couldn't read usage",
    });
  }
  expect(recordUsage).not.toHaveBeenCalled();
});

it("keeps the agents' limits together when one agent's reading isn't valid", async () => {
  reader.reading = {
    provider: "opencode",
    windows: [window(40, NaN, NaN)],
    message: null,
  };
  const answers = await usageLimits({} as ProjectChat, (provider, force) =>
    provider === "opencode"
      ? readProviderUsage(provider, force, "aggregate")
      : Promise.resolve({ provider, windows: [], message: "Sign in" }),
  );
  expect(answers.map((answer) => answer.agent)).toEqual([...usageProviders]);
  expect(answers.find((answer) => answer.agent === "opencode")).toEqual({
    agent: "opencode",
    note: "Couldn't read usage",
  });
});
