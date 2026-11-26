import {
  relayEnv,
  test,
  expect,
  _electron as electron,
} from "../fixtures/electron";
import {
  chmod,
  mkdir,
  mkdtemp,
  readFile,
  realpath,
  rm,
  writeFile,
} from "node:fs/promises";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { screenshot } from "../fixtures/screenshot";

test.skip(process.platform !== "darwin", "the trackpad's motor is macOS only");

test("the trackpad taps for a finished thread once a pattern is picked", async () => {
  const root = await realpath(await mkdtemp(join(tmpdir(), "relay-trackpad-")));
  const data = join(root, "data");
  const path = join(root, "web-store");
  const helper = join(root, "trackpad-taps");
  const calls = join(root, "calls");
  await mkdir(join(data, "project-chats"), { recursive: true });
  execFileSync("git", ["init", "-q", "-b", "main", path]);
  await writeFile(
    helper,
    `#!/bin/sh\n[ "$1" = --check ] || echo "$1" >> '${calls}'\n`,
  );
  await chmod(helper, 0o700);
  const project = {
    id: randomUUID(),
    path,
    name: "web-store",
    repository: null,
    added: 1,
  };
  await writeFile(
    join(data, "state.json"),
    JSON.stringify({
      version: 1,
      folders: {},
      progress: {},
      sidebarView: "threads",
      projects: [project],
      chats: [],
    }),
  );
  const app = await electron.launch({
    env: { ...relayEnv(), RELAY_TEST_DATA: data, RELAY_TEST_TRACKPAD: helper },
  });

  const taps = async () =>
    (await readFile(calls, "utf8").catch(() => "")).split("\n").filter(Boolean);
  let seq = 0;
  const finish = () =>
    app.evaluate(
      ({ BrowserWindow }, item) =>
        BrowserWindow.getAllWindows()[0]!.webContents.send(
          "relay:thread-news",
          item,
        ),
      {
        chatId: randomUUID(),
        projectId: project.id,
        kind: "finished",
        title: "Thread",
        body: "",
        seq: ++seq,
        at: Date.now(),
      },
    );

  try {
    const page = await app.firstWindow();
    await page.setViewportSize({ width: 1400, height: 1100 });
    await expect(
      page.getByRole("button", { name: "Project actions for Web Store" }),
    ).toBeVisible();
    await page.keyboard.press("Meta+Comma");
    const settings = page.getByRole("region", {
      name: "Settings",
      exact: true,
    });
    await settings
      .getByRole("navigation", { name: "Settings categories" })
      .getByRole("button", { name: "Sounds" })
      .click();
    const done = settings.getByRole("combobox", { name: "Trackpad: Done" });
    await expect(done).toHaveText(/Nothing/);
    expect(await taps()).toEqual([]);

    // Picking one plays it, at the strength Relay starts with.
    await done.click();
    await page.getByRole("option", { name: "Purr", exact: true }).click();
    await expect.poll(taps).toEqual(["0.3"]);
    await expect
      .poll(() => page.evaluate(() => window.relay.trackpadSettings()))
      .toEqual({ finished: "purr" });

    const strength = settings.getByRole("slider", {
      name: "Trackpad strength",
    });
    await strength.focus();
    await strength.press("ArrowRight");
    await expect.poll(async () => (await taps()).length).toBe(2);
    expect(Number((await taps())[1])).toBeCloseTo(0.35);
    await strength.scrollIntoViewIfNeeded();
    await screenshot(page, {
      path: "test-results/screenshots/trackpad-settings.png",
    });

    await finish();
    await expect.poll(async () => (await taps()).length).toBe(3);
  } finally {
    await app.close();
    await rm(root, { recursive: true, force: true });
  }
});
