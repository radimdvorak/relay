#!/usr/bin/env node
// Opens the built website (dist-website) in a headless browser and checks
// that the tour's demo window lays out: the activity bar, the sidebar and the
// thread each in their own column, at every stop. The demo mounts the app's
// own components and CSS, so a change to the app can quietly break the page
// nobody looks at; `npm run deploy:website` runs this before it uploads.
//
//   npm run build:website && node scripts/check-website.mjs [built-dir]
import { existsSync } from "node:fs";
import { join } from "node:path";
import { preview } from "vite";
import { chromium } from "playwright";

const root = join(import.meta.dirname, "..");
const outDir = process.argv[2] ?? join(root, "dist-website");
if (!existsSync(join(outDir, "index.html"))) {
  console.error(
    `No ${outDir}/index.html: run \`npm run build:website\` first.`,
  );
  process.exit(1);
}

const BAR = 52;
const SIDEBAR = 264;
// One pixel either way for borders and rounding.
const near = (a, b) => Math.abs(a - b) <= 1.5;

/** The demo window's columns, measured in the page. */
function measure() {
  const box = (selector) => {
    const element = document.querySelector(selector);
    if (!element || element.getClientRects().length === 0) return null;
    const { left, right } = element.getBoundingClientRect();
    return { left, right, width: right - left };
  };
  return {
    body: box(".rw-body"),
    bar: box(".rw-body .activity-bar"),
    sidebar: box(".rw-sidebar"),
    main: box(".rw-main"),
  };
}

function wideProblems({ body, bar, sidebar, main }) {
  if (!body || !bar || !sidebar || !main) return ["a column is missing"];
  const problems = [];
  if (!near(bar.left, body.left) || !near(bar.width, BAR))
    problems.push(
      `activity bar at ${bar.left - body.left}px, ${bar.width}px wide`,
    );
  if (!near(sidebar.left, bar.right) || !near(sidebar.width, SIDEBAR))
    problems.push(
      `sidebar at ${sidebar.left - body.left}px, ${sidebar.width}px wide`,
    );
  if (!near(main.left, sidebar.right) || !near(main.right, body.right))
    problems.push(
      `thread from ${main.left - body.left}px to ${main.right - body.left}px`,
    );
  return problems;
}

// Under 1000px the frame shows the thread alone.
function narrowProblems({ body, bar, sidebar, main }) {
  if (!body || !main) return ["the thread is missing"];
  const problems = [];
  if (bar || sidebar) problems.push("the activity bar or the sidebar shows");
  if (!near(main.left, body.left) || !near(main.right, body.right))
    problems.push(
      `thread from ${main.left - body.left}px to ${main.right - body.left}px`,
    );
  return problems;
}

const server = await preview({
  configFile: join(root, "previews/website/vite.config.ts"),
  build: { outDir },
  preview: { port: 0, host: "127.0.0.1" },
  logLevel: "silent",
});
const url = server.resolvedUrls.local[0];
const browser = await chromium.launch();
const failures = [];
try {
  for (const width of [1440, 1100, 800]) {
    // Reduced motion keeps the window from tilting as it scrolls in, which
    // would skew what's measured.
    const page = await browser.newPage({
      viewport: { width, height: 1000 },
      reducedMotion: "reduce",
    });
    await page.goto(url);
    const tabs = page.locator(".tour-list [role=tab]");
    await tabs.first().scrollIntoViewIfNeeded();
    for (let i = 0; i < (await tabs.count()); i++) {
      await tabs.nth(i).click();
      // Let the stop's first panes open.
      await page.waitForTimeout(800);
      const columns = await page.evaluate(measure);
      const problems =
        width > 1000 ? wideProblems(columns) : narrowProblems(columns);
      const stop = (await tabs.nth(i).locator("strong").textContent())?.trim();
      for (const problem of problems)
        failures.push(`${width}px, "${stop}": ${problem}`);
    }
    await page.close();
  }
} finally {
  await browser.close();
  await server.close();
}

if (failures.length) {
  console.error("The website's demo window is laid out wrong:");
  for (const failure of failures) console.error(`  ${failure}`);
  process.exit(1);
}
console.log("Website demo window lays out at every tour stop.");
