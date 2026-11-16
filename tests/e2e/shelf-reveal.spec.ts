import {
  relayEnv,
  test,
  expect,
  _electron as electron,
} from "../fixtures/electron";
import { mkdtemp, mkdir, writeFile, rm, realpath } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import type { Page } from "@playwright/test";

// Opening Settled must not take the sidebar from the cards: it shows in the
// room they leave empty, or scrolls them up by a share of the sidebar.
test.setTimeout(90_000);

const SETTLED = 40;

async function openSettledWith(
  activeCount: number,
  check: (page: Page) => Promise<void>,
) {
  const root = await realpath(await mkdtemp(join(tmpdir(), "relay-shelf-")));
  const repo = join(root, "project");
  const data = join(root, "data");
  await mkdir(repo);
  await mkdir(join(data, "project-chats"), { recursive: true });
  execFileSync("git", ["init", "-q", "-b", "main", repo]);
  const projectId = randomUUID();
  const start = Date.now() - 86_400_000;
  const chat = (title: string, settled: boolean, at: number) => ({
    id: randomUUID(),
    projectId,
    title,
    scope: { kind: "project" },
    created: at,
    updated: at + 1000,
    ...(settled ? { settledAt: at + 2000 } : {}),
    messages: [
      {
        id: `${title}-0`,
        role: "user",
        provider: "claude",
        status: "complete",
        body: "hello",
        created: at,
        version: 1,
      },
    ],
  });
  const chats = [
    ...Array.from({ length: activeCount }, (_, i) =>
      chat(`Active ${i}`, false, start + i * 10_000),
    ),
    ...Array.from({ length: SETTLED }, (_, i) =>
      chat(`Done ${i}`, true, start + i * 10_000),
    ),
  ];
  for (const c of chats)
    await writeFile(
      join(data, "project-chats", c.id + ".json"),
      JSON.stringify(c),
    );
  await writeFile(
    join(data, "state.json"),
    JSON.stringify({
      version: 1,
      folders: {},
      progress: {},
      projects: [
        {
          id: projectId,
          path: repo,
          name: "project",
          repository: null,
          added: 1,
        },
      ],
      chats: chats.map((c) => ({ ...c, messages: undefined })),
    }),
  );
  const app = await electron.launch({
    env: { ...relayEnv(), RELAY_TEST_DATA: data },
  });
  try {
    const page = await app.firstWindow();
    await page.setViewportSize({ width: 1400, height: 900 });
    await page.evaluate(() =>
      localStorage.setItem("relay-sidebar-view", "activity"),
    );
    await page.reload();
    await page.locator(".sb-shelf-toggle", { hasText: "Settled" }).click();
    await expect(
      page.locator(".sb-shelf-list .sb-compact").first(),
    ).toBeVisible();
    await check(page);
  } finally {
    await app.close();
    await rm(root, { recursive: true, force: true });
  }
}

/** Where the sidebar came to rest, once its smooth scroll has stopped. */
async function settledSidebar(page: Page) {
  const read = () =>
    page.evaluate(() => {
      const list = document.querySelector(".sb-shelf-list")!;
      const scroller = list.parentElement!;
      const box = scroller.getBoundingClientRect();
      const header = list.previousElementSibling!.getBoundingClientRect();
      const firstRow = list.firstElementChild!.getBoundingClientRect();
      return {
        scrollTop: scroller.scrollTop,
        height: scroller.clientHeight,
        headerTop: header.top - box.top,
        firstRowBottom: firstRow.bottom - box.top,
      };
    });
  let last = await read();
  await expect
    .poll(async () => {
      const now = await read();
      const still = now.scrollTop === last.scrollTop;
      last = now;
      return still;
    })
    .toBe(true);
  return last;
}

test("Settled opens in the room a few cards leave empty, without scrolling", async () => {
  await openSettledWith(3, async (page) => {
    const sidebar = await settledSidebar(page);
    expect(sidebar.scrollTop).toBe(0);
    expect(sidebar.firstRowBottom).toBeLessThan(sidebar.height);
    await expect(
      page.locator(".sb-card", { hasText: "Active 0" }),
    ).toBeInViewport();
  });
});

test("Settled scrolls a full sidebar of cards up by a share, not away", async () => {
  await openSettledWith(20, async (page) => {
    const sidebar = await settledSidebar(page);
    expect(sidebar.scrollTop).toBeGreaterThan(0);
    expect(sidebar.firstRowBottom).toBeLessThan(sidebar.height);
    // The cards keep more than half the sidebar above Settled's header.
    expect(sidebar.headerTop).toBeGreaterThan(sidebar.height * 0.5);
  });
});
