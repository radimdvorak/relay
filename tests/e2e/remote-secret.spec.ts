import {
  relayEnv,
  test,
  expect,
  _electron as electron,
  type ElectronApplication,
  type Page,
} from "../fixtures/electron";
import {
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  realpath,
  rm,
  writeFile,
} from "node:fs/promises";
import { join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { randomUUID } from "node:crypto";
import { execFileSync } from "node:child_process";
import { fakeCli, pathWith } from "../fixtures/fake-cli";
import { freePort } from "../fixtures/free-port";
import { screenshot } from "../fixtures/screenshot";
import type { ProjectChat } from "../../shared/projects";

// A thread that runs on another paired Relay asks for a secret: the desktop
// that drives it answers, the file is written where the agent runs, and
// Always remembers it there, for that computer's project.

const SECRET = "npm_Qv27-fixture-token";

/** A project with one finished thread, as the computer that owns it saved it. */
async function projectWithThread(data: string, folder: string, bin: string) {
  await mkdir(join(data, "project-chats"), { recursive: true });
  await mkdir(folder, { recursive: true });
  execFileSync("git", ["init", "-q", "-b", "main", folder]);
  const projectId = randomUUID();
  const created = Date.now() - 1000;
  const chat: ProjectChat = {
    id: randomUUID(),
    projectId,
    title: "Publish the guard",
    renamed: true,
    scope: { kind: "project" },
    created,
    updated: created + 1,
    messages: [
      {
        id: randomUUID(),
        role: "user",
        provider: "codex",
        body: "Earlier work",
        status: "complete",
        created,
        version: 1,
      },
      {
        id: randomUUID(),
        role: "assistant",
        provider: "codex",
        body: "Earlier answer",
        status: "complete",
        created: created + 1,
        version: 1,
      },
    ],
  };
  await writeFile(
    join(data, "project-chats", `${chat.id}.json`),
    JSON.stringify(chat),
  );
  await writeFile(
    join(data, "state.json"),
    JSON.stringify({
      version: 1,
      folders: {},
      progress: {},
      agentPaths: { codex: join(bin, "codex"), claude: join(bin, "claude") },
      projects: [
        {
          id: projectId,
          path: folder,
          name: "Guard",
          repository: null,
          added: 1,
        },
      ],
      chats: [{ ...chat, messages: undefined, provider: "codex" }],
    }),
  );
  return chat;
}

/** The fixture agent on the PATH, for both of its names. */
async function fixtureAgents(bin: string) {
  await mkdir(bin);
  const script = await readFile(
    resolve("tests/fixtures/room-agent.cjs"),
    "utf8",
  );
  for (const provider of ["codex", "claude"])
    await fakeCli(join(bin, provider), script);
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

/** The names a Relay keeps with Always, as its state file has them; never a value. */
async function savedNames(data: string): Promise<string[]> {
  const state = JSON.parse(await readFile(join(data, "state.json"), "utf8"));
  const projects: Record<string, { name: string }>[] = Object.values(
    state.projectSecrets ?? {},
  );
  return projects.flatMap((saved) => Object.values(saved).map((s) => s.name));
}

/** Has the thread's agent ask for a secret; the tool returns at once. */
async function askFor(page: Page, name: string) {
  await page
    .getByLabel("Message project")
    .fill(`@codex fixture relay request_secret ${JSON.stringify({ name })}`);
  await page.getByRole("button", { name: "Send message", exact: true }).click();
}

test("a thread on another computer asks for a secret, and that computer keeps it", async () => {
  test.setTimeout(180_000);
  const root = await realpath(
    await mkdtemp(join(tmpdir(), "relay-remote-secret-")),
  );
  const apps: ElectronApplication[] = [];
  try {
    const bin = join(root, "bin");
    await fixtureAgents(bin);
    const there = join(root, "there");
    const here = join(root, "here");
    const chat = await projectWithThread(there, join(root, "project"), bin);
    const launch = async (data: string) => {
      const env = relayEnv();
      const app = await electron.launch({
        env: {
          ...env,
          ...pathWith(env, bin),
          RELAY_TEST_DATA: data,
          RELAY_REMOTE_PORT: String(await freePort()),
          RELAY_REMOTE_TAILNET: "127.0.0.1",
          RELAY_AGENT_TURN_MS: "20",
          RELAY_AGENT_NO_TITLE: "1",
          RELAY_AGENT_CAPTURE: join(data, "agent.jsonl"),
        },
      });
      apps.push(app);
      const page = await app.firstWindow();
      await page.setViewportSize({ width: 1400, height: 900 });
      await expect(
        page
          .getByRole("button", { name: "Open settings", exact: true })
          .first(),
      ).toBeVisible();
      return page;
    };
    const remote = await launch(there);
    const local = await launch(here);
    const link = await remote.evaluate(async () => {
      await window.relay.setPhoneRemote(true);
      return (await window.relay.phonePairing()).url;
    });
    const computer = await local.evaluate(
      async (url) => (await window.relay.pairComputer(url)).at(-1)!,
      link,
    );
    const opened = await local.evaluate(
      ({ computerId, threadId }) =>
        window.relay.openComputerThread(computerId, threadId),
      { computerId: computer.id, threadId: chat.id },
    );
    await local.reload();
    await local.evaluate(
      (detail) =>
        window.dispatchEvent(new CustomEvent("relay:open-thread", { detail })),
      opened,
    );
    await expect(
      local.getByText("Earlier answer", { exact: true }),
    ).toBeVisible();
    const stop = local.getByRole("button", {
      name: "Stop answer",
      exact: true,
    });
    const cardFor = (name: string) =>
      local.getByRole("region", { name: `Codex asks for a secret: ${name}` });
    /** What the agent there has been told, as the fixture logged it. */
    const told = () =>
      readFile(join(there, "agent.jsonl"), "utf8").catch(() => "");

    // The card here says which computer gets the file and keeps the secret.
    await askFor(local, "npm publish token");
    const card = cardFor("npm publish token");
    await expect(card).toBeVisible({ timeout: 30_000 });
    await expect(card).toContainText(
      `It goes to a private file on ${computer.name} that Codex's commands read`,
    );
    await expect(card).toContainText(
      `Always also keeps it on ${computer.name} for this project`,
    );
    await card.getByLabel("npm publish token").fill(SECRET);
    await screenshot(card, {
      path: join(tmpdir(), "relay-remote-secret-card.png"),
    });
    await card.getByRole("button", { name: "Always" }).click();
    await expect(local.getByText('Saved "npm publish token"')).toBeVisible();
    await expect
      .poll(told)
      .toContain("They also chose to always give it in this project");
    await expect(stop).toHaveCount(0, { timeout: 30_000 });

    // The file is on the computer the agent runs on, and so is what Always
    // kept; this one saved neither.
    const [, file] = /in the chat:\\n([^\\]+)\\n/.exec(await told()) ?? [];
    expect(file.startsWith(there)).toBe(true);
    expect(await readFile(file, "utf8")).toBe(SECRET);
    expect(await filesHolding(here, SECRET)).toEqual([]);
    await expect.poll(() => savedNames(there)).toEqual(["npm publish token"]);
    expect(await savedNames(here)).toEqual([]);

    // Asked again, the computer there hands it over; no card here.
    await askFor(local, "NPM publish token");
    await expect(local.getByText('Used saved "NPM publish token"')).toBeVisible(
      { timeout: 30_000 },
    );
    await expect(cardFor("NPM publish token")).toHaveCount(0);
    await expect(stop).toHaveCount(0, { timeout: 30_000 });

    // A lookalike's card lists what that computer has saved.
    await askFor(local, "npm token");
    const lookalike = cardFor("npm token");
    await expect(lookalike).toContainText(
      "Already saved there under other names: npm publish token.",
      { timeout: 30_000 },
    );
    await screenshot(local, {
      path: join(tmpdir(), "relay-remote-secret-thread.png"),
    });
    await lookalike.getByRole("button", { name: "Decline" }).click();
    await expect(local.getByText('Declined "npm token"')).toBeVisible();
    await expect(stop).toHaveCount(0, { timeout: 30_000 });

    // The project's settings here list that computer's saved secrets, and
    // Remove forgets it there.
    await local.getByRole("button", { name: /^Project actions for/ }).click();
    await local.getByRole("menuitem", { name: "Project settings" }).click();
    const settings = local.getByRole("region", {
      name: "Settings",
      exact: true,
    });
    const saved = settings.getByRole("list", { name: "Saved secrets" });
    await expect(saved.getByRole("listitem")).toHaveCount(1);
    await expect(saved).toContainText("npm publish token");
    await saved.scrollIntoViewIfNeeded();
    await screenshot(local, {
      path: join(tmpdir(), "relay-remote-secret-settings.png"),
    });
    await saved
      .getByRole("button", { name: "Remove npm publish token" })
      .click();
    await expect(settings.getByText("None saved.")).toBeVisible();
    await expect.poll(() => savedNames(there)).toEqual([]);

    // Nothing either Relay wrote holds the value but the two files the agent
    // was pointed at: not the link's logs, nor this computer's copy of the thread.
    for (const app of apps.splice(0)) await app.close();
    const holding = await filesHolding(root, SECRET);
    expect(holding).toHaveLength(2);
    for (const path of holding)
      expect(path.startsWith(join(there, "secrets"))).toBe(true);
  } finally {
    for (const app of apps) await app.close();
    await rm(root, { recursive: true, force: true });
  }
});

test("a headless Relay keeps a secret answered with Always across its restart", async () => {
  test.setTimeout(240_000);
  execFileSync(process.execPath, ["scripts/build-headless.mjs", "9.9.9"]);
  const root = await realpath(
    await mkdtemp(join(tmpdir(), "relay-headless-secret-")),
  );
  const home = join(root, "mini-home");
  const bin = join(root, "bin");
  await fixtureAgents(bin);
  const folder = join(root, "project");
  await mkdir(folder);
  execFileSync("git", ["init", "-q", "-b", "main", folder]);
  const env = relayEnv();
  const port = String(await freePort());
  const relay = (...args: string[]) =>
    execFileSync(process.execPath, ["dist-headless/relay.cjs", ...args], {
      env: {
        ...env,
        ...pathWith(env, bin),
        RELAY_HOME: home,
        RELAY_TEST_DATA: home,
        RELAY_REMOTE_TAILNET: "127.0.0.1",
        RELAY_AGENT_TURN_MS: "20",
        RELAY_AGENT_NO_TITLE: "1",
      },
      encoding: "utf8",
    });
  const start = () => relay("start", "--port", port, "--name", "Mini");
  let laptop: ElectronApplication | undefined;
  try {
    start();
    relay("projects", "add", folder);
    const [project] = JSON.parse(relay("call", "projects")) as { id: string }[];
    const thread = JSON.parse(
      relay(
        "call",
        "createProjectChat",
        JSON.stringify(project!.id),
        JSON.stringify({ kind: "project" }),
      ),
    ) as { id: string };
    const { url } = JSON.parse(relay("pair", "--json")) as { url: string };

    laptop = await electron.launch({
      env: {
        ...env,
        ...pathWith(env, bin),
        RELAY_TEST_DATA: join(root, "laptop"),
        RELAY_REMOTE_PORT: String(await freePort()),
        RELAY_REMOTE_TAILNET: "127.0.0.1",
      },
    });
    const page = await laptop.firstWindow();
    await page.setViewportSize({ width: 1400, height: 900 });
    const opened = await page.evaluate(
      async ({ url, threadId }) => {
        const computer = (await window.relay.pairComputer(url)).at(-1)!;
        return window.relay.openComputerThread(computer.id, threadId);
      },
      { url, threadId: thread.id },
    );
    await page.reload();
    await page.evaluate(
      (detail) =>
        window.dispatchEvent(new CustomEvent("relay:open-thread", { detail })),
      opened,
    );
    const stop = page.getByRole("button", { name: "Stop answer", exact: true });

    // Answered with Always from the desktop that drives the thread.
    await askFor(page, "deploy key");
    const card = page.getByRole("region", {
      name: "Codex asks for a secret: deploy key",
    });
    await expect(card).toContainText(
      "Always also keeps it on Mini for this project",
      { timeout: 30_000 },
    );
    // It has a key file of its own to seal with, so nothing about quitting.
    await expect(card).not.toContainText("no credential store");
    await card.getByLabel("deploy key").fill(SECRET);
    await screenshot(card, {
      path: join(tmpdir(), "relay-headless-secret-card.png"),
    });
    await card.getByRole("button", { name: "Always" }).click();
    await expect(page.getByText('Saved "deploy key"')).toBeVisible();
    await expect(stop).toHaveCount(0, { timeout: 30_000 });
    expect(await savedNames(home)).toEqual(["deploy key"]);

    // Restarted, it still has it: asked again, no card.
    relay("stop", "--force");
    start();
    await askFor(page, "Deploy key");
    await expect(page.getByText('Used saved "Deploy key"')).toBeVisible({
      timeout: 60_000,
    });
    await expect(stop).toHaveCount(0, { timeout: 30_000 });
    // The fixture agent repeats what the tool told it.
    const answer = await page
      .getByRole("article", { name: "codex answer" })
      .last()
      .innerText();
    const handedOver =
      /not in the chat:\s*(\S[^\n]*?secrets\/[0-9a-f]{32})/.exec(answer)?.[1];
    expect(handedOver!.startsWith(home)).toBe(true);
    expect(await readFile(handedOver!, "utf8")).toBe(SECRET);
    await screenshot(page, {
      path: join(tmpdir(), "relay-headless-secret-used-saved.png"),
    });

    // The value is only in the files its agent was pointed at; what it kept
    // across the restart is sealed, and the laptop has none of it.
    await laptop.close();
    laptop = undefined;
    relay("stop", "--force");
    const holding = await filesHolding(root, SECRET);
    expect(holding.length).toBeGreaterThan(0);
    for (const path of holding) expect(path.startsWith(home)).toBe(true);
  } finally {
    await laptop?.close().catch(() => {});
    try {
      relay("stop", "--force");
    } catch {
      // Already stopped.
    }
    await rm(root, { recursive: true, force: true, maxRetries: 10 });
  }
});
