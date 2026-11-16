import { mkdtempSync, readdirSync, utimesSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { expect, it } from "vitest";
import { SecretFiles } from "./secret-files";

const HOUR = 60 * 60 * 1000;

it("removes a secret's file a day after it was written, and nothing else", async () => {
  const dir = mkdtempSync(join(tmpdir(), "relay-secret-files-"));
  let now = Date.now();
  const files = new SecretFiles(dir, () => now);
  const old = await files.save("old");
  writeFileSync(join(dir, "notes.txt"), "someone else's file");

  now += 23 * HOUR;
  const fresh = await files.save("fresh");
  expect(readdirSync(dir)).toHaveLength(3);

  now += 2 * HOUR;
  // A copy or a backup may reset times; what counts is when it was written.
  utimesSync(fresh, new Date(now - HOUR), new Date(now - HOUR));
  await files.removeExpired();
  const left = readdirSync(dir).map((name) => join(dir, name));
  expect(left).not.toContain(old);
  expect(left).toContain(fresh);
  expect(left).toContain(join(dir, "notes.txt"));
});
