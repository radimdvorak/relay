import { afterAll, afterEach, beforeAll, expect, it } from "vitest";
import { mkdtemp, readdir, realpath, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { AgentHosts } from "./client";

let root: string;
const opened: AgentHosts[] = [];

beforeAll(async () => {
  root = await realpath(await mkdtemp(join(tmpdir(), "relay-host-client-")));
});

afterEach(async () => {
  for (const hosts of opened.splice(0)) hosts.detach();
  const dir = join(root, "hosts");
  for (const name of await readdir(dir).catch(() => [] as string[])) {
    const pid = /^host-(\d+)\.json$/.exec(name)?.[1];
    try {
      if (pid) process.kill(Number(pid), "SIGKILL");
    } catch {}
  }
  await rm(dir, { recursive: true, force: true });
});

afterAll(() => rm(root, { recursive: true, force: true }));

/** A host that writes its record, then answers each connection with `serve`'s body. */
async function fakeHost(name: string, serve: string) {
  const script = join(root, `${name}.mjs`);
  await writeFile(
    script,
    `
    import { createServer } from "node:net";
    import { writeFileSync } from "node:fs";
    import { join } from "node:path";
    const dir = process.argv[process.argv.indexOf("--relay-agent-host") + 1];
    const socket = join(dir, "${name}.sock");
    createServer((s) => { ${serve} }).listen(socket, () =>
      writeFileSync(join(dir, "host-" + process.pid + ".json"), JSON.stringify({
        pid: process.pid, socket, token: "x", version: "v", protocol: 1, started: Date.now(),
      })),
    );
    setTimeout(() => process.exit(0), 10000);
  `,
  );
  const hosts = new AgentHosts(join(root, "hosts"), script);
  opened.push(hosts);
  return hosts;
}

it("turns down a freshly started host that hangs up, without throwing in Relay", async () => {
  const hosts = await fakeHost("hangup", "s.destroy();");
  const uncaught: unknown[] = [];
  const onUncaught = (error: unknown) => uncaught.push(error);
  process.on("uncaughtException", onUncaught);
  try {
    await expect(hosts.ensure()).rejects.toThrow("closed the connection");
    await new Promise((r) => setTimeout(r, 100));
  } finally {
    process.off("uncaughtException", onUncaught);
  }
  expect(uncaught.map(String)).toEqual([]);
});
