import { screenshot } from "../fixtures/screenshot";
import { relayEnv, test, expect, _electron as electron } from "../fixtures/electron";
import { mkdtemp, mkdir, readFile, realpath, rm } from "node:fs/promises";
import { join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { execFileSync } from "node:child_process";
import { fakeCli, pathWith } from "../fixtures/fake-cli";
import { openCodeNativeFixture } from "../fixtures/opencode-native";

const plan = (...statuses: string[]) => ({
  update: {
    sessionUpdate: "plan",
    entries: [
      "Install React reconciler in scratch demo",
      "Add per-phase telemetry and test hooks to C++ engine",
      "Measure baseline before text-cache optimization",
      "Add shaped-text cache and real React reconciler",
      "Run full automated stress and correctness suite",
    ].map((content, i) => ({ content, status: statuses[i] ?? "pending" })),
  },
});

const says = (text: string) => ({
  update: { sessionUpdate: "agent_message_chunk", content: { type: "text", text } },
});

const done = Array<string>(5).fill("completed");

/** Relay with a fake OpenCode that keeps a plan, stops in the middle of it and finishes it on request. */
async function launch() {
  const root = await realpath(await mkdtemp(join(tmpdir(), "relay-plan-")));
  const bin = join(root, "bin");
  await mkdir(bin);
  const idle = await readFile(resolve("tests/fixtures/room-agent.cjs"), "utf8");
  for (const name of ["codex", "claude"]) await fakeCli(join(bin, name), idle);
  const agent = await readFile(resolve("tests/fixtures/acp-agent.cjs"), "utf8");
  await fakeCli(join(bin, "opencode"), agent, "opencode v2.0.28");
  const native = await openCodeNativeFixture();
  const env = relayEnv();
  const app = await electron.launch({
    env: {
      ...env,
      ...pathWith(env, bin),
      RELAY_TEST_DATA: join(root, "data"),
      RELAY_ACP_SCENARIO: JSON.stringify({
        resume: true,
        sessions: ["s1"],
        nativeUrl: native.url,
        serviceUrl: native.url,
        turnByRequest: {
          "Speed up the text path": [plan("completed", "completed", "in_progress"), { waitCancel: true }],
          "Finish it": [plan(...done), says("All five are done.")],
          Thanks: [says("Any time.")],
        },
      }),
    },
  });
  const page = await app.firstWindow();
  const repo = join(root, "notes");
  execFileSync("git", ["init", "-q", "-b", "main", repo]);
  await app.evaluate(({ dialog }, folder) => {
    dialog.showOpenDialog = async () => ({ canceled: false, filePaths: [folder] });
  }, repo);
  await page.evaluate(async () => void (await window.relay.addProject()));
  return { app, page, root, native };
}

test("the thread's card follows the agent's plan until the message after it's done", async () => {
  test.setTimeout(90_000);
  const { app, page, root, native } = await launch();
  try {
    await page.setViewportSize({ width: 1500, height: 900 });
    await page.reload();
    await page.getByRole("button", { name: "Choose model and provider" }).click();
    await page
      .getByRole("toolbar", { name: "Model providers" })
      .getByRole("button", { name: "OpenCode", exact: true })
      .click();
    await page.getByRole("option").first().click();
    const send = async (text: string) => {
      await page.getByLabel("Message project").fill(text);
      await page.getByRole("button", { name: "Send message", exact: true }).click();
    };

    await send("Speed up the text path");
    const section = page.locator(".plan-section");
    await expect(section.getByRole("button")).toHaveText("Plan3 of 5");
    const states = () =>
      section.locator("li").evaluateAll((steps) => steps.map((s) => (s as HTMLElement).dataset.state));
    expect(await states()).toEqual(["done", "done", "now", "next", "next"]);
    await screenshot(page, { path: join(tmpdir(), "relay-plan-card.png") });

    await section.getByRole("button").click();
    await expect(section.locator("li")).toHaveCount(0);
    await section.getByRole("button").click();
    await expect(section.locator("li")).toHaveCount(5);

    await page.getByRole("button", { name: "Fold the card" }).click();
    const stripe = page.getByRole("button", {
      name: "This computer · 3 of 5: Measure baseline before text-cache optimization",
    });
    await expect(stripe).toContainText("3/5");
    await screenshot(page, { path: join(tmpdir(), "relay-plan-stripe.png") });
    await stripe.click();

    // Stopped halfway, the plan stays where it got to, through a reload too.
    await page.getByRole("button", { name: "Stop answer", exact: true }).click();
    await expect(page.getByRole("button", { name: "Stop answer", exact: true })).toHaveCount(0);
    await page.reload();
    await expect(section.getByRole("button")).toHaveText("Plan3 of 5");

    await send("Finish it");
    await expect(page.getByText("All five are done.")).toBeVisible();
    await expect(section.getByRole("button")).toHaveText("Plan5 of 5");
    await send("Thanks");
    await expect(page.getByText("Any time.")).toBeVisible();
    await expect(section).toHaveCount(0);
  } finally {
    await app.close();
    await native.close();
    await rm(root, { recursive: true, force: true });
  }
});
