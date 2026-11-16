import {
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
  readdir,
  rm,
  realpath,
  stat,
} from "node:fs/promises";
import { join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { execFileSync } from "node:child_process";
import { fakeCli, pathWith } from "../fixtures/fake-cli";
import { screenshot } from "../fixtures/screenshot";

// An agent asks for a secret only the user has (request_secret): the user
// answers in a card, the value goes to a private file outside the project,
// and neither the agent's messages nor anything Relay saves holds it.

const SECRET = "npm_Zx81-fixture-token";

/** Relay on a fresh project whose agent answers from the room fixture. */
async function openProject(root: string) {
  const bin = join(root, "bin"),
    repo = join(root, "project");
  await mkdir(bin);
  const script = await readFile(
    resolve("tests/fixtures/room-agent.cjs"),
    "utf8",
  );
  for (const name of ["codex", "claude"])
    await fakeCli(join(bin, name), script);
  await mkdir(repo);
  const git = (...args: string[]) =>
    execFileSync("git", ["-C", repo, ...args], { encoding: "utf8" });
  git("init", "-q", "-b", "main");
  git("config", "user.name", "Fixture");
  git("config", "user.email", "fixture@example.invalid");
  await writeFile(join(repo, "README.md"), "fixture\n");
  git("add", ".");
  git("commit", "-qm", "Base");
  const env = Object.fromEntries(
    Object.entries(process.env).filter(
      ([k, v]) =>
        k !== "ELECTRON_RUN_AS_NODE" &&
        k !== "RELAY_DEV_URL" &&
        v !== undefined,
    ),
  ) as Record<string, string>;
  const app = await electron.launch({
    env: {
      ...env,
      ...pathWith(env, bin),
      RELAY_TEST_DATA: join(root, "data"),
      RELAY_TEST_HEADED: process.env.RELAY_TEST_HEADED ?? "0",
      RELAY_AGENT_CAPTURE: join(root, "agent.jsonl"),
    },
  });
  const page = await app.firstWindow();
  await page.setViewportSize({ width: 1400, height: 900 });
  await app.evaluate(({ dialog }, folder) => {
    dialog.showOpenDialog = async () => ({
      canceled: false,
      filePaths: [folder],
    });
  }, repo);
  await page.evaluate(() => window.relay.addProject());
  await page.reload();
  return { app, page, repo };
}

/** Has the thread's agent ask for a secret; the tool returns at once. */
async function askFor(page: Page, input: { name: string; reason?: string }) {
  await page
    .getByLabel("Message project")
    .fill(`fixture relay request_secret ${JSON.stringify(input)}`);
  await page.getByRole("button", { name: "Send message", exact: true }).click();
}

/** Every file under `dir` whose text holds `needle`. */
async function filesHolding(dir: string, needle: string): Promise<string[]> {
  const found: string[] = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) found.push(...(await filesHolding(path, needle)));
    else if (entry.isFile() && (await readFile(path)).includes(needle))
      found.push(path);
  }
  return found;
}

test("an agent asks for a secret and gets a file's path, never the value", async () => {
  test.setTimeout(120_000);
  const root = await realpath(await mkdtemp(join(tmpdir(), "relay-secret-")));
  let app: ElectronApplication | undefined;
  try {
    const opened = await openProject(root);
    app = opened.app;
    const page = opened.page;
    const answers = page.getByRole("article", { name: "codex answer" });
    const stop = page.getByRole("button", { name: "Stop answer", exact: true });
    /** What the agent has been told, as the fixture logged it. */
    const told = () =>
      readFile(join(root, "agent.jsonl"), "utf8").catch(() => "");

    await askFor(page, {
      name: "npm publish token",
      reason: "To publish 2.1.0. Make one at npmjs.com under Access Tokens.",
    });
    await expect(answers.last()).toContainText(
      'The user sees a card asking for "npm publish token"',
      { timeout: 30_000 },
    );
    await expect(stop).toHaveCount(0, { timeout: 30_000 });
    const card = page.getByRole("region", {
      name: "Codex asks for a secret: npm publish token",
    });
    await expect(card).toBeVisible();
    const field = card.getByLabel("npm publish token");
    // Hidden from the screen and from screen readers, and not a login field
    // a password manager would fill.
    await expect(field).toHaveAttribute("type", "password");
    await expect(field).toHaveAttribute("autocomplete", "off");
    await expect(field).toHaveAttribute("data-1p-ignore", "true");
    const save = card.getByRole("button", { name: "Save" });
    await expect(save).toBeDisabled();
    await field.fill(SECRET);
    await screenshot(page, {
      path: join(tmpdir(), "relay-request-secret.png"),
    });
    await screenshot(card, {
      path: join(tmpdir(), "relay-request-secret-card.png"),
    });
    await save.click();

    // The agent hears where the file is; the thread says only that it was saved.
    await expect(page.getByText('Saved "npm publish token"')).toBeVisible();
    await expect(card).toHaveCount(0);
    await expect.poll(told).toContain('The user saved \\"npm publish token\\"');
    const [, file] = /in the chat:\\n([^\\]+)\\n/.exec(await told()) ?? [];
    expect(await readFile(file, "utf8")).toBe(SECRET);
    expect((await stat(file)).mode & 0o777).toBe(0o600);
    expect(file.startsWith(join(root, "data"))).toBe(true);
    expect(file.startsWith(opened.repo)).toBe(false);
    expect(await told()).not.toContain(SECRET);
    await expect(answers).toHaveCount(2, { timeout: 30_000 });
    await expect(stop).toHaveCount(0, { timeout: 30_000 });

    // Declining tells the agent to go on without it, not to ask in chat.
    await askFor(page, { name: "Staging password" });
    const second = page.getByRole("region", {
      name: "Codex asks for a secret: Staging password",
    });
    await second.getByRole("button", { name: "Decline" }).click();
    await expect(page.getByText('Declined "Staging password"')).toBeVisible();
    await expect
      .poll(told)
      .toContain('The user declined to give \\"Staging password\\"');
    await expect(answers).toHaveCount(4, { timeout: 30_000 });
    await expect(stop).toHaveCount(0, { timeout: 30_000 });
    await page.reload();
    await expect(page.getByText('Saved "npm publish token"')).toBeVisible();
    await screenshot(page, {
      path: join(tmpdir(), "relay-request-secret-answered.png"),
    });

    // Nothing Relay wrote holds the value but that one file: not the saved
    // thread, its log, the agent's messages or the project.
    await app.close();
    app = undefined;
    expect(await filesHolding(root, SECRET)).toEqual([file]);
  } finally {
    await app?.close();
    await rm(root, { recursive: true, force: true });
  }
});

test("a secret answered with Always comes back without a card, under its own name only", async () => {
  test.setTimeout(120_000);
  const root = await realpath(await mkdtemp(join(tmpdir(), "relay-secret-")));
  let app: ElectronApplication | undefined;
  try {
    const opened = await openProject(root);
    app = opened.app;
    const page = opened.page;
    const answers = page.getByRole("article", { name: "codex answer" });
    const stop = page.getByRole("button", { name: "Stop answer", exact: true });
    const told = () =>
      readFile(join(root, "agent.jsonl"), "utf8").catch(() => "");
    const cardFor = (name: string) =>
      page.getByRole("region", { name: `Codex asks for a secret: ${name}` });

    await askFor(page, { name: "npm publish token" });
    const card = cardFor("npm publish token");
    // The card says which saved secret Always makes it.
    await expect(card).toContainText(
      "any agent here that asks for “npm publish token” by exactly that name gets it without a card",
    );
    const actions = card.locator(".secret-request-actions button");
    await expect(actions).toHaveText(["Decline", "Always", "Save"]);
    await card.getByLabel("npm publish token").fill(SECRET);
    await screenshot(card, {
      path: join(tmpdir(), "relay-request-secret-always-card.png"),
    });
    await card.getByRole("button", { name: "Always" }).click();
    await expect(page.getByText('Saved "npm publish token"')).toBeVisible();
    await expect
      .poll(told)
      .toContain("They also chose to always give it in this project");
    await expect(answers).toHaveCount(2, { timeout: 30_000 });
    await expect(stop).toHaveCount(0, { timeout: 30_000 });

    // Asked again, however it is cased: no card, the tool answers with a
    // fresh file, and the thread keeps a row that it happened.
    await askFor(page, { name: "NPM publish token" });
    await expect(answers.last()).toContainText(
      'The user chose to always give "NPM publish token" in this project',
      { timeout: 30_000 },
    );
    await expect(cardFor("NPM publish token")).toHaveCount(0);
    await expect(
      page.getByText('Used saved "NPM publish token"'),
    ).toBeVisible();
    const handedOver =
      /not in the chat:\s*(\S[^\n]*?secrets\/[0-9a-f]{32})/.exec(
        (await answers.last().innerText()) ?? "",
      )?.[1];
    expect(await readFile(handedOver!, "utf8")).toBe(SECRET);
    await expect(stop).toHaveCount(0, { timeout: 30_000 });
    await screenshot(page, {
      path: join(tmpdir(), "relay-request-secret-used-saved.png"),
    });

    // A lookalike name is another secret: it gets a card, which shows what
    // is already saved so the difference is plain.
    await askFor(page, { name: "npm token" });
    const lookalike = cardFor("npm token");
    await expect(lookalike).toContainText(
      "Already saved here under other names: npm publish token.",
    );
    await screenshot(lookalike, {
      path: join(tmpdir(), "relay-request-secret-lookalike-card.png"),
    });
    await lookalike.getByRole("button", { name: "Decline" }).click();
    await expect(page.getByText('Declined "npm token"')).toBeVisible();
    await expect(stop).toHaveCount(0, { timeout: 30_000 });

    // Project settings lists what is saved, by name, and removes it.
    await page.getByRole("button", { name: /^Project actions for/ }).click();
    await page.getByRole("menuitem", { name: "Project settings" }).click();
    const settings = page.getByRole("region", {
      name: "Settings",
      exact: true,
    });
    const saved = settings.getByRole("list", { name: "Saved secrets" });
    await expect(saved.getByRole("listitem")).toHaveCount(1);
    await expect(saved).toContainText("npm publish token");
    await saved.scrollIntoViewIfNeeded();
    await screenshot(page, {
      path: join(tmpdir(), "relay-request-secret-settings.png"),
    });
    await saved
      .getByRole("button", { name: "Remove npm publish token" })
      .click();
    await expect(settings.getByText("None saved.")).toBeVisible();
    await settings.getByRole("button", { name: "Back to app" }).click();

    // Removed, it is asked for again.
    await askFor(page, { name: "npm publish token" });
    await expect(cardFor("npm publish token")).toBeVisible({ timeout: 30_000 });

    // The value is on disk only in the two files the agent was pointed at;
    // what Relay saved of it for the project was sealed, and is gone.
    await app.close();
    app = undefined;
    const holding = await filesHolding(root, SECRET);
    expect(holding).toHaveLength(2);
    expect(holding).toContain(handedOver);
  } finally {
    await app?.close();
    await rm(root, { recursive: true, force: true });
  }
});
