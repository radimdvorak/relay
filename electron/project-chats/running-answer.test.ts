import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it, vi } from "vitest";
import type { HtmlRender } from "../../shared/html-render";
import type { ChatCore } from "./core";
import { rememberedSecrets } from "./remembered-secrets";
import { ask, showRender } from "./running-answer";

// The OS credential store, standing in: sealed text is the value reversed.
vi.mock("../platform/seal", () => {
  const reversed = async (text: string) => [...text].reverse().join("");
  return { seal: reversed, unseal: reversed, canSeal: async () => true };
});

const page: HtmlRender = { id: "r1", title: "Chart", created: 1, pages: [{}] };

/** A thread with an answer running, under the render mode the user picked. */
function runningThread(renderMode: "auto" | "off") {
  const active = { render: vi.fn(), askPage: vi.fn() };
  const save = vi.fn(async () => {});
  const core = {
    store: { get: () => ({ renderMode }) },
    active: { get: () => active, windingDown: () => false },
    storage: { renders: { save } },
  } as unknown as ChatCore;
  return { core, active, save };
}

// A session started before the user turned pages off still lists the tools.
it("shows no page once the user turned pages off", async () => {
  const { core, active, save } = runningThread("off");
  await expect(showRender(core, "chat-1", page, ["<p>hi</p>"])).rejects.toThrow(
    /pages off/,
  );
  await expect(
    ask(core, "chat-1", { page, html: "<p>hi</p>", ask: "Pick one" }),
  ).rejects.toThrow(/pages off/);
  expect(save).not.toHaveBeenCalled();
  expect(active.render).not.toHaveBeenCalled();
  expect(active.askPage).not.toHaveBeenCalled();
});

it("shows the page while pages are on", async () => {
  const { core, active } = runningThread("auto");
  await showRender(core, "chat-1", page, ["<p>hi</p>"]);
  expect(active.render).toHaveBeenCalledWith(page);
});

describe("a secret the agent asks for", () => {
  const VALUE = "ghp_s3cr3t-value";
  const secret = { name: "GitHub token", reason: "To publish the release." };

  /** A running thread in a project whose store remembers what Always saved. */
  function thread() {
    const active = { askPage: vi.fn() };
    const state: Record<string, unknown> = {};
    const core = {
      store: {
        dir: mkdtempSync(join(tmpdir(), "relay-running-answer-")),
        get: () => state,
        update: async (change: (s: typeof state) => void) => change(state),
      },
      active: { get: () => active, windingDown: () => false },
      storage: { load: async () => ({ projectId: "project" }) },
    } as unknown as ChatCore;
    return { core, active };
  }

  it("leaves a card when nothing is remembered under that name", async () => {
    const { core, active } = thread();
    await rememberedSecrets(core.store).remember("project", "GitHub", VALUE);
    expect(await ask(core, "chat-1", { secret })).toBeUndefined();
    const [group] = active.askPage.mock.calls[0];
    expect(group).toMatchObject({ secret, questions: [{ isSecret: true }] });
    expect(group.answers).toBeUndefined();
  });

  it("hands a remembered one over in a file, with a row in the answer and no card", async () => {
    const { core, active } = thread();
    await rememberedSecrets(core.store).remember("project", secret.name, VALUE);
    const file = await ask(core, "chat-1", { secret });
    expect(readFileSync(file!, "utf8")).toBe(VALUE);
    expect(file!.startsWith(core.store.dir)).toBe(true);
    const [group] = active.askPage.mock.calls[0];
    expect(group.answers).toEqual({ secret: ["Remembered"] });
    expect(JSON.stringify(active.askPage.mock.calls)).not.toContain(VALUE);
  });
});
