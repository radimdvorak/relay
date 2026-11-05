// @vitest-environment happy-dom
import { afterEach, expect, it, vi } from "vitest";
import type { TerminalOpened } from "../../../shared/terminals";
import { api } from "../../lib/api";
import { closeTerminal, terminalFor } from "./thread-terminals";

vi.mock("../../lib/api", () => ({
  api: {
    onTerminal: vi.fn(),
    openTerminal: vi.fn(),
    closeTerminal: vi.fn(async () => {}),
    resizeTerminal: vi.fn(async () => {}),
    writeTerminal: vi.fn(async () => {}),
    ackTerminal: vi.fn(async () => {}),
  },
}));

afterEach(() => vi.clearAllMocks());

it("ends a shell that finished starting after its tab was closed", async () => {
  let opened!: (value: TerminalOpened) => void;
  vi.mocked(api.openTerminal).mockReturnValue(
    new Promise((resolve) => (opened = resolve)),
  );
  const terminal = terminalFor("project", "chat", "two");
  const starting = terminal.start();
  closeTerminal("project", "chat", "two");
  expect(api.closeTerminal).toHaveBeenCalledTimes(1);

  opened({ cwd: "/repo", backlog: "" });
  await starting;

  expect(api.closeTerminal).toHaveBeenCalledTimes(2);
  expect(api.closeTerminal).toHaveBeenLastCalledWith("chat~two");
});

it("leaves the shell to a tab that took its place meanwhile", async () => {
  let opened!: (value: TerminalOpened) => void;
  vi.mocked(api.openTerminal).mockReturnValueOnce(
    new Promise((resolve) => (opened = resolve)),
  );
  const first = terminalFor("project", "chat");
  const starting = first.start();
  closeTerminal("project", "chat", "");
  terminalFor("project", "chat");

  opened({ cwd: "/repo", backlog: "" });
  await starting;

  expect(api.closeTerminal).toHaveBeenCalledTimes(1);
});
