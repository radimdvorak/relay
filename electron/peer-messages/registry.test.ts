import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, expect, it } from "vitest";
import { addressee, listedSessions, type ListedSession } from "./registry";

const sessions: ListedSession[] = [
  {
    sessionId: "aad49b11-0000",
    name: "mudtoy-d4",
    socket: "\\\\.\\pipe\\LOCAL\\cc-msg-d4",
    updatedAt: 2,
  },
  { sessionId: "1c565500-0000", name: "mudtoy-11", updatedAt: 5 },
  { sessionId: "ffff0000-0000", name: "mudtoy-11", updatedAt: 1 },
];

it("finds a session by its name, the latest when two share it", () => {
  expect(addressee(sessions, "mudtoy-d4")?.sessionId).toBe("aad49b11-0000");
  expect(addressee(sessions, " mudtoy-11 ")?.sessionId).toBe("1c565500-0000");
});

it("takes the listing's ref to tell sessions with one name apart", () => {
  expect(addressee(sessions, "mudtoy-11 [ffff00]")?.sessionId).toBe(
    "ffff0000-0000",
  );
});

it("finds the session a reply goes to by the address its message came from", () => {
  expect(addressee(sessions, "uds:\\\\.\\pipe\\LOCAL\\cc-msg-d4")?.name).toBe(
    "mudtoy-d4",
  );
});

it("finds nothing for a subagent or teammate", () => {
  expect(addressee(sessions, "main")).toBeUndefined();
  expect(addressee(sessions, "uds:/tmp/gone.sock")).toBeUndefined();
});

let home: string | undefined;

afterEach(async () => {
  if (home) await rm(home, { recursive: true, force: true });
  home = undefined;
});

it("reads each running session's listing once, skipping what isn't one", async () => {
  home = await mkdtemp(join(tmpdir(), "relay-peers-"));
  const dir = join(home, "sessions");
  await mkdir(dir);
  const entry = (pid: number, updatedAt: number) =>
    JSON.stringify({
      pid,
      sessionId: "s-1",
      name: "relay-88",
      messagingSocketPath: "/tmp/s1.sock",
      updatedAt,
    });
  await writeFile(join(dir, "10.json"), entry(10, 1));
  await writeFile(join(dir, "11.json"), entry(11, 9));
  await writeFile(join(dir, "12.json"), "{ not json");
  await writeFile(
    join(dir, "13.json"),
    JSON.stringify({ sessionId: "nameless" }),
  );
  await writeFile(join(dir, "10.abc.key"), "secret");
  // The same folder through two accounts counts once.
  const listed = await listedSessions([home, home]);
  expect(listed).toEqual([
    {
      sessionId: "s-1",
      name: "relay-88",
      socket: "/tmp/s1.sock",
      updatedAt: 9,
    },
  ]);
});
