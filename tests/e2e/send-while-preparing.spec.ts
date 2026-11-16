import {
  relayEnv,
  test,
  expect,
  _electron as electron,
  type ElectronApplication,
  type Page,
} from "../fixtures/electron";
import {
  mkdtemp,
  mkdir,
  writeFile,
  readFile,
  rm,
  realpath,
} from "node:fs/promises";
import { join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { execFileSync } from "node:child_process";
import { fixtureServer } from "../fixtures/gitea";
import { fakeCli, pathWith } from "../fixtures/fake-cli";

/**
 * Holds every message to a worker until `releasePreparation()`, so a
 * screenshot heavy enough to be compressed stays in preparation until then.
 */
function holdPreparation(page: Page) {
  return page.evaluate(() => {
    const scope = window as unknown as {
      releasePreparation: () => void;
    };
    let release!: () => void;
    const gate = new Promise<void>((resolve) => (release = resolve));
    scope.releasePreparation = release;
    const post = Worker.prototype.postMessage;
    Worker.prototype.postMessage = function (
      this: Worker,
      ...args: Parameters<typeof post>
    ) {
      void gate.then(() => post.apply(this, args));
    } as typeof post;
  });
}

/** Pastes a noisy screenshot, over the size that gets compressed in a worker. */
function pasteHeavyShot(page: Page) {
  return page.getByLabel("Message project").evaluate(async (element) => {
    const canvas = new OffscreenCanvas(800, 800);
    const g = canvas.getContext("2d")!;
    const pixels = g.createImageData(800, 800);
    for (let i = 0; i < pixels.data.length; i++)
      pixels.data[i] = i % 4 === 3 ? 255 : Math.random() * 255;
    g.putImageData(pixels, 0, 0);
    const blob = await canvas.convertToBlob({ type: "image/png" });
    const clipboard = new DataTransfer();
    clipboard.items.add(new File([blob], "image.png", { type: "image/png" }));
    element.dispatchEvent(
      new ClipboardEvent("paste", {
        bubbles: true,
        cancelable: true,
        clipboardData: clipboard,
      }),
    );
  });
}

test("sends a message whose screenshot was still being prepared when Enter was pressed", async () => {
  const root = await realpath(
    await mkdtemp(join(tmpdir(), "relay-prep-send-")),
  );
  const fixture = await fixtureServer();
  const bin = join(root, "bin"),
    repo = join(root, "web-store"),
    capture = join(root, "capture.jsonl");
  let app: ElectronApplication | undefined;
  try {
    await mkdir(bin);
    await fakeCli(
      join(bin, "codex"),
      await readFile(resolve("tests/fixtures/room-agent.cjs"), "utf8"),
    );
    await mkdir(repo);
    execFileSync("git", ["init", "-q", "-b", "main", repo]);
    await writeFile(join(repo, "README.md"), "# Fixture project\n");
    execFileSync("git", ["-C", repo, "add", "."]);
    execFileSync("git", [
      "-C",
      repo,
      "-c",
      "user.name=Fixture",
      "-c",
      "user.email=fixture@example.invalid",
      "commit",
      "-qm",
      "Initial",
    ]);
    const env = relayEnv();
    app = await electron.launch({
      env: {
        ...env,
        ...pathWith(env, bin),
        RELAY_TEST_DATA: join(root, "data"),
        RELAY_AGENT_CAPTURE: capture,
      },
    });
    const page = await app.firstWindow();
    await app.evaluate(({ dialog }, repo) => {
      dialog.showOpenDialog = async () => ({
        canceled: false,
        filePaths: [repo],
      });
    }, repo);
    await page.evaluate(async (url) => {
      await window.relay.connect(url, "test-token");
      await window.relay.addProject();
    }, fixture.serverUrl);
    await page.reload();
    await page
      .locator(".sb-project-row")
      .getByRole("button", { name: "Web Store", exact: true })
      .click();
    const input = page.getByLabel("Message project");
    await input.click();
    await input.pressSequentially("What's wrong here? ");

    await holdPreparation(page);
    await pasteHeavyShot(page);
    // The screenshot's placeholder: it is being prepared as Enter goes.
    await expect(
      page.getByRole("status", { name: "Preparing screenshot" }),
    ).toBeVisible();
    await page.keyboard.press("Enter");
    await page.evaluate(() =>
      (
        window as unknown as { releasePreparation: () => void }
      ).releasePreparation(),
    );

    const message = page.getByRole("article", { name: "Your message" }).last();
    await expect(message).toContainText("What's wrong here?");
    await expect(message.locator("button.prompt-pill.image")).toHaveCount(1);
    await expect(
      page.getByText("The cache guard prevents duplicate requests.", {
        exact: true,
      }),
    ).toBeVisible();
    await expect(input).toHaveText("");
    const turn = (await readFile(capture, "utf8"))
      .trim()
      .split("\n")
      .map((line) => JSON.parse(line))
      .filter((c) => c.turn)
      .map((c) => c.turn.input as { type: string; text?: string }[])
      .filter((input) => !input[0].text?.startsWith("Generate a short title"))
      .at(-1)!;
    expect(turn[0].text).toContain("What's wrong here? [Image #1]");
    expect(turn.filter((item) => item.type === "localImage")).toHaveLength(1);
  } finally {
    await app?.close();
    await fixture.close();
    await rm(root, { recursive: true, force: true });
  }
});
