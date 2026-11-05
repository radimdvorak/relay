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

it("sends a paste too big for one write in pieces, none of it lost", async () => {
  vi.mocked(api.openTerminal).mockResolvedValue({ cwd: "/repo", backlog: "" });
  const terminal = terminalFor("project", "paste");
  await terminal.start();
  const pasted = `${"a".repeat((1 << 20) - 1)}😀${"b".repeat(300_000)}`;

  (terminal as unknown as { term: { input(data: string): void } }).term.input(
    pasted,
  );

  const writes = vi
    .mocked(api.writeTerminal)
    .mock.calls.map(([, data]) => data);
  expect(writes.every((data) => data.length <= 1 << 20)).toBe(true);
  expect(writes.join("")).toBe(pasted);
  expect(writes[0].endsWith("a")).toBe(true);
});
