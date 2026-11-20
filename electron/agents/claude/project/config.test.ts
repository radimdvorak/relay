import { expect, it } from "vitest";
import {
  runtimeModeSchema,
  type AgentRuntimeMode,
} from "../../../../shared/agent-modes";
import {
  claudePermissionMode,
  linkedFolders,
  sessionConfig,
  sessionSignature,
  type ClaudeRunOptions,
} from "./config";
import { RELAY_SERVER, WAIT_LIMIT_SECONDS } from "../../../relay-mcp";

// Read only reaches an agent as Supervised with `readOnly`; see turn-run.
const agentModes = runtimeModeSchema.options.filter(
  (mode): mode is AgentRuntimeMode => mode !== "read-only",
);

it("maps each approval mode to a Claude permission mode, and Plan to plan", () => {
  expect(
    agentModes.map((mode) =>
      claudePermissionMode({ runtimeMode: mode, interactionMode: "default" }),
    ),
  ).toEqual(["default", "acceptEdits", "auto", "bypassPermissions"]);
  expect(
    claudePermissionMode({
      runtimeMode: "full-access",
      interactionMode: "plan",
    }),
  ).toBe("plan");
});

it("keeps a read-only reviewer asking canUseTool whatever its approval mode", () => {
  for (const runtimeMode of agentModes)
    expect(
      claudePermissionMode({
        runtimeMode,
        interactionMode: "default",
        readOnly: true,
      }),
    ).toBe("default");
});

it("writes linked folders as working directories, and lets read-only ones be read without asking", () => {
  expect(
    linkedFolders([
      { path: "/Users/me/api", access: "write" },
      { path: "/Users/me/types", access: "read" },
      { path: "C:\\work\\shared", access: "read" },
    ]),
  ).toEqual({
    additionalDirectories: ["/Users/me/api"],
    settings: {
      permissions: {
        allow: ["Read(//Users/me/types/**)", "Read(//c/work/shared/**)"],
      },
    },
  });
  expect(linkedFolders([])).toEqual({});
});

it("restarts the session when a link's folder or access changes, not its note", () => {
  const base = { runtimeMode: "auto", interactionMode: "default" } as const;
  const api = { path: "/w/api", access: "read" } as const;
  const signature = (links: ClaudeRunOptions["links"]) =>
    sessionSignature({ ...base, links } as ClaudeRunOptions);
  expect(signature([api])).not.toBe(signature([]));
  expect(signature([{ ...api, access: "write" }])).not.toBe(signature([api]));
  expect(signature([{ ...api, note: "the backend" }])).toBe(signature([api]));
});

it("sets a side conversation up as its thread, so its fork reads the thread's cache", () => {
  const thread: ClaudeRunOptions = {
    job: { kind: "prompt" },
    cwd: "/project",
    prompt: "",
    choice: {} as never,
    model: "opus",
    effort: "high",
    signal: new AbortController().signal,
    onText() {},
    runtimeMode: "full-access",
    interactionMode: "default",
    relayTools: { url: "http://127.0.0.1:1/mcp", token: "t", renders: "auto" },
  };
  const side: ClaudeRunOptions = {
    ...thread,
    job: { kind: "side" },
    readOnly: true,
  };
  const { permissionMode: threadMode, ...threadSetup } = sessionConfig(
    thread,
    "/usr/bin/claude",
    true,
    {},
  );
  const { permissionMode: sideMode, ...sideSetup } = sessionConfig(
    side,
    "/usr/bin/claude",
    true,
    {},
  );
  // Same tools, MCP servers and system prompt; only asking differs.
  expect(sideSetup).toEqual(threadSetup);
  expect(threadMode).toBe("bypassPermissions");
  expect(sideMode).toBe("default");
});

it("starts Claude Code with its own advisor, or with none while Relay asks another", () => {
  const options = {
    job: { kind: "prompt" },
    cwd: "/project",
    prompt: "",
    choice: {} as never,
    model: "sonnet",
    effort: "",
    signal: new AbortController().signal,
    onText() {},
    runtimeMode: "auto",
    interactionMode: "default",
    links: [{ path: "/w/api", access: "read" }],
  } satisfies ClaudeRunOptions;
  const settingsFor = (advisor?: ClaudeRunOptions["advisor"]) =>
    sessionConfig({ ...options, advisor }, "/usr/bin/claude", false, {})
      .settings as { advisorModel?: string; permissions?: unknown };
  expect(settingsFor({ provider: "claude", model: "opus" })).toMatchObject({
    advisorModel: "opus",
    permissions: { allow: expect.arrayContaining(["Read(//w/api/**)"]) },
  });
  // Off says so, or Claude Code's own setting would pick one; so does an
  // advisor Relay asks, or Claude would have two.
  expect(settingsFor("off")?.advisorModel).toBe("");
  expect(
    settingsFor({ provider: "codex", model: "gpt-6-luna" })?.advisorModel,
  ).toBe("");
  expect(settingsFor()).not.toHaveProperty("advisorModel");
});

it("places Default as the model it runs: Claude Code keeps a weaker advisor off", () => {
  const options = {
    job: { kind: "prompt" },
    cwd: "/project",
    prompt: "",
    choice: {} as never,
    model: "",
    effort: "",
    signal: new AbortController().signal,
    onText() {},
    runtimeMode: "auto",
    interactionMode: "default",
    advisor: { provider: "claude", model: "sonnet" },
  } satisfies ClaudeRunOptions;
  const advisorModel = (defaultModel?: string) =>
    (
      sessionConfig({ ...options, defaultModel }, "/usr/bin/claude", false, {})
        .settings as { advisorModel?: string }
    ).advisorModel;
  // On Opus, Relay asks Sonnet.
  expect(advisorModel("opus")).toBe("");
  expect(advisorModel("sonnet")).toBe("sonnet");
  // Not known, Claude Code gets the benefit of the doubt.
  expect(advisorModel()).toBe("sonnet");
});

it("lets a Relay tool call run past the longest wait", () => {
  const setup = sessionConfig(
    {
      job: { kind: "prompt" },
      cwd: "/project",
      prompt: "",
      choice: {} as never,
      model: "opus",
      effort: "high",
      signal: new AbortController().signal,
      onText() {},
      runtimeMode: "full-access",
      interactionMode: "default",
      relayTools: {
        url: "http://127.0.0.1:1/mcp",
        token: "t",
        renders: "auto",
      },
    },
    "/usr/bin/claude",
    true,
    {},
  );
  const relay = setup.mcpServers?.[RELAY_SERVER];
  expect(relay && "timeout" in relay && relay.timeout).toBeGreaterThan(
    WAIT_LIMIT_SECONDS * 1000,
  );
});
