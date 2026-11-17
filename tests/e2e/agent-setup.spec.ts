import {
  relayEnv,
  test,
  expect,
  _electron as electron,
} from "../fixtures/electron";
import { mkdtemp, mkdir, writeFile, realpath } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { execFileSync } from "node:child_process";
import { screenshot } from "../fixtures/screenshot";

test("the picker's Set up in Settings closes the picker and shows that agent", async () => {
  const root = await realpath(
    await mkdtemp(join(tmpdir(), "relay-agent-setup-")),
  );
  const repo = join(root, "project");
  await mkdir(repo);
  const git = (...args: string[]) =>
    execFileSync("git", ["-C", repo, ...args], { encoding: "utf8" });
  git("init", "-q", "-b", "main");
  git("config", "user.name", "Test");
  git("config", "user.email", "test@example.invalid");
  await writeFile(join(repo, "README.md"), "# Setup\n");
  git("add", ".");
  git("commit", "-qm", "Base");

  // A fresh data folder, where Relay hasn't downloaded Antigravity.
  const app = await electron.launch({
    env: { ...relayEnv(), RELAY_TEST_DATA: join(root, "data") },
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

    await page
      .getByRole("button", { name: "Choose model and provider" })
      .click();
    await page
      .getByRole("toolbar", { name: "Model providers" })
      .getByRole("button", { name: /Antigravity/ })
      .click();
    const setUp = page.getByRole("button", { name: "Set up in Settings" });
    await setUp.click();

    await expect(setUp).toBeHidden();
    await expect(
      page.getByRole("listbox", { name: "Agents" }).getByRole("option", {
        selected: true,
      }),
    ).toContainText("Antigravity");
    await screenshot(page, {
      path: join(tmpdir(), "relay-agent-setup-settings.png"),
    });
  } finally {
    await app.close();
  }
});
