// @vitest-environment happy-dom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { ClockifyStatus } from "../../../shared/clockify";
import { clockifyKey, useClockifyProjects } from "./plugins";

const mocks = vi.hoisted(() => ({ projects: vi.fn(), status: vi.fn() }));

vi.mock("../../lib/api", () => ({
  api: { clockifyProjects: mocks.projects, clockifyStatus: mocks.status },
}));

const statusIn = (workspaceId: string) =>
  ({
    settings: { host: "global", workspaceId, idleMinutes: 15, projects: {} },
    hasToken: true,
    persistent: true,
    day: null,
    review: null,
  }) as unknown as ClockifyStatus;

let host: HTMLDivElement;
let root: Root;
let client: QueryClient;
let names: string[] | undefined;

/** Lets the queries answer and the hook render what they said. */
const settle = () =>
  act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 20));
  });

function Names() {
  names = useClockifyProjects().data?.map((p) => p.name);
  return null;
}

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  host = document.createElement("div");
  document.body.append(host);
  root = createRoot(host);
});

afterEach(() => {
  act(() => root.unmount());
  host.remove();
  client.clear();
});

it("lists the projects of the workspace now chosen", async () => {
  let workspace = "a";
  mocks.status.mockImplementation(async () => statusIn(workspace));
  mocks.projects.mockImplementation(async () => [
    { id: `${workspace}1`, name: `Project in ${workspace}` },
  ]);
  client.setQueryData(clockifyKey, statusIn("a"));
  await act(async () =>
    root.render(
      <QueryClientProvider client={client}>
        <Names />
      </QueryClientProvider>,
    ),
  );
  await settle();
  expect(names).toEqual(["Project in a"]);

  // Settings saves another workspace; the status shows it at once.
  workspace = "b";
  await act(async () => client.setQueryData(clockifyKey, statusIn("b")));
  await settle();
  expect(names).toEqual(["Project in b"]);
});
