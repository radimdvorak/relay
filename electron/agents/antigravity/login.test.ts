import { EventEmitter } from "node:events";
import { PassThrough } from "node:stream";
import { afterEach, expect, it, vi } from "vitest";
import { spawnExecutable } from "../../platform/executables";
import { startCodeLogin } from "./login";

vi.mock("../../platform/executables", () => ({ spawnExecutable: vi.fn() }));

vi.mock("./install", () => ({
  ensureAntigravity: async () => ({ version: "1", command: "agy", args: [] }),
}));

afterEach(() => vi.useRealTimers());

function cli() {
  const child = Object.assign(new EventEmitter(), {
    stdin: new PassThrough(),
    stdout: new PassThrough(),
    stderr: new PassThrough(),
  });
  vi.mocked(spawnExecutable).mockReturnValue(child as never);
  return child;
}

it("leaves no timer behind when the CLI couldn't start", async () => {
  vi.useFakeTimers();
  const child = cli();
  const login = await startCodeLogin({}, async () => {});
  child.emit("error", new Error("spawn agy ENOENT"));
  await expect(login.done).rejects.toThrow("ENOENT");
  expect(vi.getTimerCount()).toBe(0);
});

it("survives a code entered as the CLI exits, and says what the CLI said", async () => {
  const child = cli();
  const login = await startCodeLogin({}, async () => {});
  child.stderr.write("couldn't log in: Malformed auth code.\n");
  await vi.waitFor(() => expect(child.stderr.readableLength).toBe(0));
  login.enterCode("late");
  child.stdin.emit("error", new Error("write EPIPE"));
  child.emit("exit", 1);
  await expect(login.done).rejects.toThrow("Malformed auth code.");
});
