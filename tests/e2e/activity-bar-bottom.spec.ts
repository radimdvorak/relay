import {
  relayEnv,
  test,
  expect,
  _electron as electron,
} from "../fixtures/electron";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import type { Page } from "@playwright/test";

const shots = process.env.RELAY_E2E_SHOTS;

/** A Relay whose settings.json starts with the bar along the sidebar's bottom. */
async function launchWithBottomBar(name: string) {
  const data = await mkdtemp(join(tmpdir(), `relay-bar-${name}-`));
  await mkdir(join(data, "config"), { recursive: true });
  await writeFile(
    join(data, "config", "settings.json"),
    JSON.stringify({ activityBar: { position: "bottom" } }),
  );
  const app = await electron.launch({
    env: { ...relayEnv(), RELAY_TEST_DATA: data },
  });
  const page = await app.firstWindow();
  await page.setViewportSize({ width: 1200, height: 800 });
  return { app, page, data };
}

/** The bar's icons as they read, in order. */
const iconNames = (page: Page) =>
  page
    .getByRole("navigation", { name: "Activity bar" })
    .locator(".activity-bar-slot button, .activity-bar-more")
    .evaluateAll((buttons) => buttons.map((b) => b.getAttribute("aria-label")));

test("along the bottom the bar lies at the sidebar's foot and goes with it", async () => {
  const { app, page, data } = await launchWithBottomBar("foot");
  try {
    const bar = page.getByRole("navigation", { name: "Activity bar" });
    const sidebar = page.locator(".projects-sidebar");
    const layout = page.locator(".project-layout");
    await expect(bar).toBeVisible();
    // No column of its own: the sidebar starts at the window's edge.
    await expect(layout).toHaveCSS("padding-left", "0px");
    const barBox = (await bar.boundingBox())!;
    const sideBox = (await sidebar.boundingBox())!;
    expect(barBox.x).toBe(sideBox.x);
    expect(barBox.y + barBox.height).toBeCloseTo(sideBox.y + sideBox.height, 0);
    expect(barBox.height).toBeLessThan(40);
    if (shots) await page.screenshot({ path: join(shots, "bottom.png") });

    await page.getByTitle(/^Hide sidebar/).click();
    await expect(bar).toBeHidden();
    await expect(sidebar).toBeHidden();

    // The edge peeks both, and the pointer can go down to the bar.
    const { y, height } = (await layout.boundingBox())!;
    await page.mouse.move(4, y + height / 2);
    await expect(sidebar).toBeVisible();
    await expect(bar).toBeVisible();
    await expect(bar).toHaveCSS("transform", "none");
    const peeked = (await bar.boundingBox())!;
    await page.mouse.move(peeked.x + 40, peeked.y + peeked.height / 2, {
      steps: 8,
    });
    await page.waitForTimeout(400);
    await expect(bar).toBeVisible();
    if (shots) await page.screenshot({ path: join(shots, "peek.png") });

    // Settings' nav takes the sidebar's place, and the bar stays at its foot.
    await bar.getByRole("button", { name: "Open settings" }).click();
    await page.mouse.move(1000, 400, { steps: 6 });
    await page.waitForTimeout(400);
    await expect(bar).toBeVisible();
    const nav = (await page.locator(".settings-nav").boundingBox())!;
    const inSettings = (await bar.boundingBox())!;
    expect(inSettings.x).toBe(nav.x);
    expect(inSettings.y + inSettings.height).toBeCloseTo(nav.y + nav.height, 0);
    if (shots) await page.screenshot({ path: join(shots, "settings.png") });
  } finally {
    await app.close();
    await rm(data, { recursive: true, force: true });
  }
});

test("dragging an icon reorders the bar and keeps the order", async () => {
  const { app, page, data } = await launchWithBottomBar("drag");
  try {
    const bar = page.getByRole("navigation", { name: "Activity bar" });
    await expect(bar).toBeVisible();
    expect(await iconNames(page)).toEqual([
      "Search threads",
      "Pull requests",
      "Schedules",
      "Usage",
    ]);

    await bar
      .getByRole("button", { name: "Usage" })
      .dragTo(bar.getByRole("button", { name: "Search threads" }), {
        targetPosition: { x: 2, y: 14 },
      });
    await expect
      .poll(() => iconNames(page))
      .toEqual(["Usage", "Search threads", "Pull requests", "Schedules"]);
    // Saved once it's let go, so the next window starts with it.
    await expect
      .poll(async () => {
        const file = await readFile(
          join(data, "config", "settings.json"),
          "utf8",
        );
        return JSON.parse(file).activityBar?.order?.slice(0, 3);
      })
      // Threads keeps its place for the left edge, where it shows.
      .toEqual(["threads", "usage", "search"]);
    if (shots) await page.screenshot({ path: join(shots, "dragged.png") });
  } finally {
    await app.close();
    await rm(data, { recursive: true, force: true });
  }
});

test("what doesn't fit along the bottom waits behind a … button", async () => {
  const { app, page, data } = await launchWithBottomBar("more");
  try {
    const bar = page.getByRole("navigation", { name: "Activity bar" });
    await expect(bar).toBeVisible();
    await expect(bar.getByRole("button", { name: "More" })).toHaveCount(0);

    // Narrower than the sidebar can get, to make it overflow.
    await bar.evaluate((nav) => (nav.style.width = "160px"));
    const more = bar.getByRole("button", { name: "More" });
    await expect(more).toBeVisible();
    const shown = await iconNames(page);
    expect(shown.at(-1)).toBe("More");
    expect(shown).not.toContain("Usage");

    await more.click();
    const menu = page.getByRole("menu");
    await expect(menu.getByRole("menuitem", { name: "Usage" })).toBeVisible();
    if (shots) await page.screenshot({ path: join(shots, "more.png") });
    await menu.getByRole("menuitem", { name: "Usage" }).click();
    // The page it opened lights the "…" up, since its icon is inside.
    await expect(more).toHaveClass(/active/);
  } finally {
    await app.close();
    await rm(data, { recursive: true, force: true });
  }
});
