// @vitest-environment happy-dom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { CommandOption } from "../../../shared/commands";
import type { Project } from "../../../shared/projects";
import { api } from "../../lib/api";
import { useResumeCommand } from "./useResumeCommand";

vi.mock("../../lib/api", () => ({ api: { terminalSessions: vi.fn() } }));

const listed = vi.mocked(api.terminalSessions);

const here: Project = {
  id: "p1",
  name: "Sample",
  path: "/tmp/sample",
} as Project;

const remote: Project = {
  ...here,
  computer: { id: "c1", name: "remote-computer", projectId: "p9" },
};

let container: HTMLDivElement;
let root: ReturnType<typeof createRoot>;
let shown: CommandOption[] = [];
let errors: unknown[] = [];

/** Asks for the `/resume` options on every render, as the composer's menu does. */
function Menu({ project }: { project: Project }) {
  const resume = useResumeCommand({
    project,
    settingsKey: "t1",
    workspace: "checkout",
    onContinued: async () => {},
    onOpenThread: async () => {},
    onError: (e) => errors.push(e),
  });
  shown = resume.options("resume", "") ?? [];
  return null;
}

async function open(project: Project) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={qc}>
        <Menu project={project} />
      </QueryClientProvider>,
    );
  });
  // Lets a failed lookup land and any re-render it causes run.
  for (let i = 0; i < 5; i++)
    await act(() => new Promise((r) => setTimeout(r)));
}

beforeEach(() => {
  container = document.createElement("div");
  root = createRoot(container);
  shown = [];
  errors = [];
  listed.mockReset();
});

afterEach(() => act(() => root.unmount()));

it("says the lookup failed instead of looking forever, and asks once", async () => {
  listed.mockRejectedValue(new Error("Couldn't read the sessions folder"));
  await open(here);
  expect(listed).toHaveBeenCalledTimes(1);
  expect(shown.map((o) => o.label)).toEqual(["Couldn't look for sessions"]);
  expect(shown[0].description).toBe("Couldn't read the sessions folder");
});

it("doesn't look on this computer for a project on another one", async () => {
  await open(remote);
  expect(listed).not.toHaveBeenCalled();
  expect(errors).toEqual([]);
  expect(shown[0].label).toBe(
    "Open Sample on remote-computer to resume its sessions",
  );
});
