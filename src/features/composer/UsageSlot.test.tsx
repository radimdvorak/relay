// @vitest-environment happy-dom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { ProviderUsage } from "../../../shared/provider-usage";
import type { ThreadCost } from "../../../shared/thread-cost";
import type { ThreadAccounts } from "../accounts/useThreadAccounts";
import { api } from "../../lib/api";
import { usageControl } from "./UsageSlot";
import type { AgentRuns } from "./useAgentRuns";
import type { ModelCatalogs } from "./useModelCatalogs";

vi.mock("../../lib/while-shown", () => ({
  everyWhileShown: () => () => {},
}));

vi.mock("../../lib/api", () => ({ api: { providerUsage: vi.fn() } }));

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

const HOUR = 60 * 60 * 1000;

const accounts = {
  of: () => undefined,
  state: undefined,
} as unknown as ThreadAccounts;

const runs = {
  choiceFor: () => undefined,
  claudeRuns: undefined,
  claude: { model: "" },
} as unknown as AgentRuns;

const catalogs = {
  of: () => undefined,
  defaults: { of: () => undefined },
} as unknown as ModelCatalogs;

const cost: ThreadCost = {
  usd: 0.42,
  answers: 2,
  models: [{ name: "Paid model", usd: 0.42 }],
};

const limits = (provider: "opencode" | "claude"): ProviderUsage => ({
  provider,
  message: null,
  windows: [
    {
      kind: "session",
      usedPercent: 30,
      resetsAt: Date.now() + 2 * HOUR,
      periodMs: 5 * HOUR,
    },
    {
      kind: "weekly",
      usedPercent: 50,
      resetsAt: Date.now() + 70 * HOUR,
      periodMs: 168 * HOUR,
    },
  ],
});

const noLimits = (message: string): ProviderUsage => ({
  provider: "opencode",
  windows: [],
  message,
});

let root: Root;
let host: HTMLElement;
const usage = vi.mocked(api.providerUsage);

beforeEach(() => {
  host = document.createElement("div");
  root = createRoot(host);
});

afterEach(async () => {
  await act(async () => root.unmount());
  vi.clearAllMocks();
});

/** What the toolbar's usage control shows for a thread, once readings arrive. */
async function show(props: {
  provider: string;
  computer?: { id: string };
  cost?: ThreadCost;
}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const control = usageControl({ runs, catalogs, accounts, ...props });
  await act(async () =>
    root.render(
      <QueryClientProvider client={client}>{control}</QueryClientProvider>,
    ),
  );
  // The reading resolves on the query client's next tick.
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 20));
  });
  const labels = [...host.querySelectorAll("button")].map(
    (button) => button.getAttribute("aria-label") ?? "",
  );
  return {
    control,
    ring: labels.some((label) => / usage[:,]/.test(label)),
    cost: labels.find((label) => label.startsWith("This thread cost")),
  };
}

it("keeps the thread's price when OpenCode reports no limits", async () => {
  usage.mockResolvedValue(noLimits("No Go limits reported for this workspace"));
  const shown = await show({ provider: "opencode", cost });
  expect(shown.cost).toBe("This thread cost $0.42");
  expect(shown.ring).toBe(false);
  expect(usage).toHaveBeenCalledWith("opencode", false, expect.anything());
});

it("keeps the thread's price beside Go's limits for a model outside the plan", async () => {
  usage.mockResolvedValue(limits("opencode"));
  const shown = await show({ provider: "opencode", cost });
  expect(shown.ring).toBe(true);
  expect(shown.cost).toBe("This thread cost $0.42");
});

it("shows only the ring when nothing has cost", async () => {
  usage.mockResolvedValue(limits("opencode"));
  const shown = await show({ provider: "opencode" });
  expect(shown.ring).toBe(true);
  expect(shown.cost).toBeUndefined();
});

it("shows nothing at all when there are no limits and nothing has cost", async () => {
  usage.mockResolvedValue(noLimits("Shows while OpenCode is running"));
  const shown = await show({ provider: "opencode" });
  expect(shown.ring).toBe(false);
  expect(shown.cost).toBeUndefined();
  expect(host.querySelector("button")).toBeNull();
});

it("doesn't show this computer's Go limits for a project on another", async () => {
  usage.mockResolvedValue(limits("opencode"));
  const shown = await show({
    provider: "opencode",
    computer: { id: "mini" },
    cost,
  });
  expect(shown.ring).toBe(false);
  expect(shown.cost).toBe("This thread cost $0.42");
  expect(usage).not.toHaveBeenCalled();
});

it("leaves the toolbar slot empty for a remote OpenCode thread with no cost", async () => {
  const shown = await show({ provider: "opencode", computer: { id: "mini" } });
  expect(shown.control).toBeNull();
  expect(usage).not.toHaveBeenCalled();
});

it("still shows the other agents' limits, on this computer or another", async () => {
  usage.mockResolvedValue(limits("claude"));
  expect((await show({ provider: "claude" })).ring).toBe(true);
  await act(async () => root.unmount());
  root = createRoot(host);
  expect(
    (await show({ provider: "claude", computer: { id: "mini" } })).ring,
  ).toBe(true);
});

it("shows the price alone for an agent without limits", async () => {
  const shown = await show({ provider: "amp", cost });
  expect(shown.ring).toBe(false);
  expect(shown.cost).toBe("This thread cost $0.42");
  expect((await show({ provider: "amp" })).control).toBeNull();
});
