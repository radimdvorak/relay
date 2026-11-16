import {
  relayEnv,
  test,
  expect,
  _electron as electron,
} from "../fixtures/electron";
import { mkdtemp, mkdir, readFile, rm, realpath } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { execFileSync } from "node:child_process";
import { fakeCli, pathWith } from "../fixtures/fake-cli";
import { screenshot } from "../fixtures/screenshot";

test("shows Claude's advisor peeking over the composer", async () => {
  const root = await realpath(await mkdtemp(join(tmpdir(), "relay-advisor-")));
  const repo = join(root, "project"),
    bin = join(root, "bin");
  await Promise.all([mkdir(repo), mkdir(bin)]);
  execFileSync("git", ["init", "-q", "-b", "main", repo]);
  await fakeCli(
    join(bin, "claude"),
    await readFile(resolve("tests/fixtures/slow-claude.cjs"), "utf8"),
  );
  const env = relayEnv();
  const app = await electron.launch({
    env: {
      ...env,
      ...pathWith(env, bin),
      RELAY_TEST_DATA: join(root, "data"),
      SLOW_CLAUDE_MS: "1",
      SLOW_CLAUDE_MODELS: JSON.stringify([
        {
          value: "sonnet",
          displayName: "Sonnet",
          resolvedModel: "claude-sonnet-5-5",
        },
        {
          value: "opus",
          displayName: "Opus",
          description: "Opus 5.5 · For complex work",
          resolvedModel: "claude-opus-5-5",
        },
      ]),
    },
  });
  try {
    const page = await app.firstWindow();
    await app.evaluate(({ dialog }, repo) => {
      dialog.showOpenDialog = async () => ({
        canceled: false,
        filePaths: [repo],
      });
    }, repo);
    await page.evaluate(() => window.relay.addProject());
    await page.reload();
    const input = page.getByLabel("Message project");
    await input.fill("/provider cl");
    await input.press("Enter");
    await input.fill("Plan the cache");
    await input.press("Enter");
    // Picked once the first answer is in: its settings land on the composer.
    await expect(page.getByText(/eighteen nineteen twenty/)).toBeVisible({
      timeout: 20000,
    });
    await page.waitForTimeout(500);
    await input.fill("/advisor opus");
    await input.press("Enter");
    const peek = page.getByLabel("Opus 5.5 advises");
    await expect(peek).toBeVisible();
    const composer = page.locator(".thread-compose-wrap");
    const shot = async (name: string) =>
      screenshot(page, {
        path: join(tmpdir(), name),
        clip: await composer.boundingBox().then((box) => ({
          x: box!.x,
          y: box!.y - 50,
          width: box!.width,
          height: box!.height + 50,
        })),
      });
    await page.emulateMedia({ colorScheme: "dark" });
    await shot("relay-advisor-peek.png");
    await peek.hover();
    await expect(page.getByText(/Erm, actually/)).toBeVisible();
    await page.waitForTimeout(300);
    await shot("relay-advisor-peek-hover.png");
    // Off, it goes back down.
    await input.fill("/advisor off");
    await input.press("Enter");
    await expect(peek).toHaveCount(0);
  } finally {
    await app.close();
    await rm(root, { recursive: true, force: true });
  }
});
