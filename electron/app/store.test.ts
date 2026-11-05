import { chmod, mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { Store } from "./store";

let dir: string;

beforeEach(async () => {
  dir = await mkdtemp(join(tmpdir(), "relay-store-"));
});

afterEach(async () => {
  await rm(dir, { recursive: true, force: true });
});

describe("Store", () => {
  it("has nothing left to save after a change refused before writing", async () => {
    const store = new Store(dir);
    await store.load();
    await store.update((s) => {
      s.gitPath = "/usr/bin/git";
    });

    await expect(
      store.update(() => {
        throw new Error("That folder is no longer linked to this thread.");
      }),
    ).rejects.toThrow("no longer linked");

    await expect(store.flush()).resolves.toBeUndefined();
    const saved = JSON.parse(await readFile(join(dir, "state.json"), "utf8"));
    expect(saved.gitPath).toBe("/usr/bin/git");
  });

  it.skipIf(process.platform === "win32")(
    "still reports a change that couldn't be written",
    async () => {
      const store = new Store(dir);
      await store.load();
      await chmod(dir, 0o500);
      try {
        await expect(
          store.update((s) => {
            s.gitPath = "/usr/bin/git";
          }),
        ).rejects.toThrow();
        await expect(store.flush()).rejects.toThrow();
      } finally {
        await chmod(dir, 0o700);
      }
    },
  );
});
