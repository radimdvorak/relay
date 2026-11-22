import { beforeEach, expect, it, vi } from "vitest";

const cli = vi.hoisted(() => ({
  calls: [] as string[][],
  status: "stopped",
  version: vi.fn(),
}));

// The OpenCode CLI, as far as the service lookup talks to it.
vi.mock("node:child_process", () => ({
  execFile: (
    _command: string,
    args: string[],
    _options: object,
    done: (error: Error | null, result?: { stdout: string }) => void,
  ) => {
    cli.calls.push(args);
    const line = args.join(" ");
    if (line === "service status") return done(null, { stdout: cli.status });
    if (line === "service get password")
      return done(null, { stdout: "secret" });
    if (line === "api GET /api/info") {
      // Asking the CLI to call the service is what starts a stopped one.
      cli.status = "http://127.0.0.1:4096";
      return done(null, { stdout: JSON.stringify({ version: "2.3.2" }) });
    }
    return done(new Error(`unexpected: ${line}`));
  },
}));

vi.mock("../../platform/executables", () => ({
  findExecutable: async () => "opencode",
  executableCommand: (command: string, args: string[]) => ({ command, args }),
}));

vi.mock("./command", () => ({ openCodeVersion: cli.version }));

const { OpenCodeStoppedError, openCodeEndpoint, staleService } =
  await import("./native-api");

beforeEach(() => {
  cli.calls = [];
  cli.status = "stopped";
  cli.version.mockReset().mockResolvedValue("2.3.2");
});

it("tells a service an older OpenCode left running from the installed one", () => {
  const info = (version: string) => JSON.stringify({ version, pid: 1 });
  expect(staleService("2.1.4", info("2.0.32"))).toBe(true);
  expect(staleService("2.1.4", info("2.1.4"))).toBe(false);
  // Nothing to go by: leave the service alone.
  expect(staleService("", info("2.0.32"))).toBe(false);
  expect(staleService("2.1.4", "not json")).toBe(false);
  expect(staleService("2.1.4", "{}")).toBe(false);
});

it("starts a stopped service when the lookup isn't passive", async () => {
  const { url } = await openCodeEndpoint();
  expect(url.href).toBe("http://127.0.0.1:4096/");
  expect(cli.calls).toContainEqual(["api", "GET", "/api/info"]);
});

it("leaves a stopped service stopped when the lookup is passive", async () => {
  await expect(openCodeEndpoint({ passive: true })).rejects.toBeInstanceOf(
    OpenCodeStoppedError,
  );
  expect(cli.calls).toEqual([["service", "status"]]);
  expect(cli.status).toBe("stopped");
});

it("finds a running service without checking its version when passive", async () => {
  cli.status = "http://127.0.0.1:4096";
  const found = await openCodeEndpoint({ passive: true });
  expect(found.password).toBe("secret");
  // A stale service is neither asked about nor restarted.
  expect(cli.version).not.toHaveBeenCalled();
  expect(cli.calls).toEqual([
    ["service", "status"],
    ["service", "get", "password"],
  ]);
});
