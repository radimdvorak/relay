import { chmod, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { Trackpad } from ".";

describe.skipIf(process.platform !== "darwin")("Trackpad", () => {
  let dir: string;

  beforeEach(async () => {
    dir = await mkdtemp(join(tmpdir(), "relay-trackpad-"));
  });

  afterEach(() => rm(dir, { recursive: true, force: true }));

  async function helper(script: string) {
    const path = join(dir, "trackpad-taps");
    await writeFile(path, `#!/bin/sh\n${script}\n`);
    await chmod(path, 0o700);
    return path;
  }

  const calls = () => readFile(join(dir, "calls"), "utf8").catch(() => "");

  it("hands the helper the strength and the pattern's steps", async () => {
    const path = await helper(`echo "$@" >> '${dir}/calls'`);
    new Trackpad(path).tap("thud", 0.4);
    await expect.poll(calls).toBe("0.4 70:60:1.000:70 50:90:0.500:90\n");
  });

  it("cuts off a pattern still playing", async () => {
    const path = await helper(
      `trap 'echo cut >> "${dir}/calls"; exit' TERM\necho start >> '${dir}/calls'\nwhile :; do sleep 0.02; done`,
    );
    const trackpad = new Trackpad(path);
    trackpad.tap("purr", 1);
    await expect.poll(calls).toBe("start\n");
    trackpad.tap("thud", 1);
    await expect.poll(calls).toContain("cut");
  });

  it("is unavailable when the helper finds no motor, or isn't there", async () => {
    expect(await new Trackpad(await helper("exit 0")).available()).toBe(true);
    expect(await new Trackpad(await helper("exit 1")).available()).toBe(false);
    const missing = new Trackpad(join(dir, "missing"));
    expect(await missing.available()).toBe(false);
    expect(() => missing.tap("purr", 1)).not.toThrow();
  });
});
