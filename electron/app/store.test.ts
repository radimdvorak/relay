import { chmod, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
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

  describe("after a power cut empties state.json", () => {
    const launch = async () => {
      const store = new Store(dir);
      await store.load();
      return store;
    };

    it("starts from the copy kept at the last good launch", async () => {
      const first = await launch();
      await first.update((s) => {
        s.gitPath = "/usr/bin/git";
      });
      await launch(); // keeps the copy
      await writeFile(join(dir, "state.json"), "");
      const warn = vi.spyOn(console, "warn").mockImplementation(() => {});

      const recovered = await launch();

      expect(recovered.get().gitPath).toBe("/usr/bin/git");
      expect(recovered.savedAtLoad).toBe(0);
      expect(warn).toHaveBeenCalledWith(
        expect.stringContaining("starting from state.json.bak"),
      );
      warn.mockRestore();
    });

    it("keeps the copy that loaded, not a later damaged state.json", async () => {
      const first = await launch();
      await first.update((s) => {
        s.gitPath = "/usr/bin/git";
      });
      await launch();
      await writeFile(join(dir, "state.json"), '{"version":1,"fol');
      vi.spyOn(console, "warn").mockImplementation(() => {});
      await launch();
      await launch();
      vi.restoreAllMocks();

      const kept = JSON.parse(
        await readFile(join(dir, "state.json.bak"), "utf8"),
      );
      expect(kept.gitPath).toBe("/usr/bin/git");
    });

    it("refuses to start without a readable copy, and touches neither file", async () => {
      await writeFile(join(dir, "state.json"), "");
      await writeFile(join(dir, "state.json.bak"), '{"vers');

      await expect(launch()).rejects.toThrow("no readable earlier copy");
      expect(await readFile(join(dir, "state.json"), "utf8")).toBe("");
      expect(await readFile(join(dir, "state.json.bak"), "utf8")).toBe(
        '{"vers',
      );
    });
  });
});
