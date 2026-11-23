// @vitest-environment happy-dom
import { act, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { usageProviders } from "../../../shared/agents";
import { api } from "../../lib/api";
import { ModelPickerFooter } from "./ModelPickerFooter";
import { usageShownFor } from "./provider-usage";
import { usePickerUsage } from "./usePickerUsage";

vi.mock("../../lib/while-shown", () => ({
  everyWhileShown: () => () => {},
}));

vi.mock("../../lib/api", () => ({ api: { providerUsage: vi.fn() } }));

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

let root: Root;
let host: HTMLElement;
const usage = vi.mocked(api.providerUsage);

beforeEach(() => {
  host = document.createElement("div");
  root = createRoot(host);
  usage.mockImplementation(async (provider) => ({
    provider,
    windows: [],
    message: `${provider} says`,
  }));
});

afterEach(async () => {
  await act(async () => root.unmount());
  vi.clearAllMocks();
});

const asked = () => new Set(usage.mock.calls.map(([provider]) => provider));

async function render(node: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  await act(async () =>
    root.render(
      <QueryClientProvider client={client}>{node}</QueryClientProvider>,
    ),
  );
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 20));
  });
}

let latest: ReturnType<typeof usePickerUsage>;

function Probe({ computer }: { computer?: { id: string } }) {
  latest = usePickerUsage(true, undefined, computer);
  return null;
}

it("reads every agent that reports usage for a project on this computer", async () => {
  await render(<Probe />);
  expect(asked()).toEqual(new Set(usageProviders));
  expect(asked().has("opencode")).toBe(true);
  expect(Object.keys(latest.usage).sort()).toEqual([...usageProviders].sort());
});

it("doesn't read this computer's OpenCode for a project on another", async () => {
  await render(<Probe computer={{ id: "mini" }} />);
  expect(asked().has("opencode")).toBe(false);
  expect(latest.usage.opencode).toBeUndefined();
  // The other agents are read as they always were.
  expect(asked()).toEqual(
    new Set(usageProviders.filter((provider) => provider !== "opencode")),
  );
  expect(latest.usage.claude?.message).toBe("claude says");
});

it("says the limits aren't read from other computers, rather than that OpenCode reports none", async () => {
  const footer = (computer?: { id: string; name: string }) => (
    <ModelPickerFooter
      category="favorites"
      provider="opencode"
      computer={computer}
      usage={{ usage: {}, now: Date.now() }}
    />
  );
  await render(footer({ id: "mini", name: "Mini" }));
  expect(host.textContent).toContain("Not read from other computers");
  expect(host.textContent).not.toContain("Doesn't report usage");
  await act(async () => root.unmount());
  root = createRoot(host);
  await render(footer());
  expect(host.textContent).not.toContain("Not read from other computers");
});

it("knows which agents' limits are the project computer's", () => {
  expect(usageShownFor("opencode")).toBe(true);
  expect(usageShownFor("opencode", { id: "mini" })).toBe(false);
  expect(usageShownFor("claude", { id: "mini" })).toBe(true);
  expect(usageShownFor("amp")).toBe(false);
  expect(usageShownFor("message")).toBe(false);
});
