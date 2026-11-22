import { expect, it, vi } from "vitest";
import type { ModelChoice, ReasoningEffort } from "../../../shared/settings";
import type { AcpConnection } from "./connection";
import type { AcpSettings } from "./sessions";
import {
  applySettings,
  currentOf,
  effortsForModels,
  modelsOf,
} from "./settings";

const select = (id: string, category: string, currentValue: string, values: string[]) => ({
  id,
  category,
  type: "select",
  currentValue,
  options: values.map((value) => ({ value, name: value.toUpperCase() })),
});

/** A connection that records requests and answers set_config_option with no options. */
function recording() {
  const request = vi.fn(async (..._: unknown[]): Promise<unknown> => ({}));
  return { connection: { request } as unknown as AcpConnection, request };
}

const choice = (model = "", reasoningEffort: ReasoningEffort | "" = ""): ModelChoice => ({
  model,
  reasoningEffort,
  fast: false,
});

it("goes back to the session's own model and effort when Default is picked again", async () => {
  const { connection, request } = recording();
  const session = {
    id: "s",
    settings: {
      configOptions: [
        select("model", "model", "pro", ["pro", "flash"]),
        select("effort", "thought_level", "medium", ["low", "medium", "high"]),
      ],
    } as AcpSettings,
  };

  await applySettings(connection, session, [], choice("flash", "high"));
  await applySettings(connection, session, [], choice());

  expect(request.mock.calls.map((c) => (c[1] as { value: string }).value)).toEqual([
    "flash",
    "high",
    "pro",
    "medium",
  ]);
});

it("does the same through the older model list and mode list some agents still send", async () => {
  const { connection, request } = recording();
  const session = {
    id: "s",
    settings: {
      modes: {
        currentModeId: "default",
        availableModes: [{ id: "default" }, { id: "yolo" }],
      },
      models: {
        currentModelId: "auto",
        availableModels: [{ modelId: "auto" }, { modelId: "flash" }],
      },
    } as AcpSettings,
  };

  await applySettings(connection, session, [{ option: "mode", value: "yolo" }], choice("flash"));
  await applySettings(connection, session, [{ option: "mode", value: "nonsense" }], choice());

  expect(request.mock.calls).toEqual([
    ["session/set_mode", { sessionId: "s", modeId: "yolo" }],
    ["session/set_model", { sessionId: "s", modelId: "flash" }],
    ["session/set_model", { sessionId: "s", modelId: "auto" }],
  ]);
});

it("offers efforts only when every level is one Relay knows", () => {
  const known = modelsOf({
    configOptions: [
      select("model", "model", "a", ["a"]),
      select("effort", "thought_level", "low", ["low", "high"]),
    ],
  } as AcpSettings);
  const odd = modelsOf({
    configOptions: [
      select("model", "model", "a", ["a"]),
      select("effort", "thought_level", "low", ["low", "turbo"]),
    ],
  } as AcpSettings);

  expect(known[0]).toMatchObject({ id: "a", name: "A", efforts: ["low", "high"] });
  expect(odd[0].efforts).toEqual([]);
});

it("offers OpenCode's levels alongside its explicit default without duplicating Default", () => {
  const settings = {
    configOptions: [
      select("model", "model", "openai/gpt", ["openai/gpt"]),
      select("effort", "thought_level", "default", ["low", "medium", "high", "default"]),
    ],
  } as AcpSettings;

  expect(modelsOf(settings)[0].efforts).toEqual(["low", "medium", "high"]);
  expect(currentOf(settings)).toEqual({ model: "openai/gpt", effort: "" });
  settings.configOptions![1].options = [{ value: "default", name: "Default" }];
  expect(modelsOf(settings)[0].efforts).toEqual([]);
});

it("asks each model for its own effort levels", async () => {
  const settings = {
    configOptions: [
      select("model", "model", "gemini", ["deepseek", "gemini"]),
      select("effort", "thought_level", "default", [
        "low",
        "medium",
        "high",
        "xhigh",
        "default",
      ]),
    ],
  } as AcpSettings;
  const original = { ...settings };
  const request = vi.fn(async () => {
    settings.configOptions = [
      select("model", "model", "deepseek", ["deepseek", "gemini"]),
      select("effort", "thought_level", "default", [
        "low",
        "high",
        "max",
        "default",
      ]),
    ];
    return { configOptions: settings.configOptions };
  });

  const found = await effortsForModels(
    { request } as unknown as AcpConnection,
    { id: "s", settings },
    1_000,
  );

  expect(request.mock.calls).toEqual([
    [
      "session/set_config_option",
      { sessionId: "s", configId: "model", value: "deepseek" },
    ],
  ]);
  expect(
    modelsOf(original, found).map(({ id, efforts }) => [id, efforts]),
  ).toEqual([
    ["deepseek", ["low", "high", "max"]],
    ["gemini", ["low", "medium", "high", "xhigh"]],
  ]);
});

it("surveys models even when the default has no effort option", async () => {
  const ids = ["plain", "deepseek", "other-plain"];
  const settings = { configOptions: [select("model", "model", "plain", ids)] };
  const request = vi.fn(async (_: string, { value }: { value: string }) => ({
    configOptions: [
      select("model", "model", value, ids),
      ...(value === "deepseek"
        ? [select("effort", "thought_level", "default", ["low", "high", "max"])]
        : []),
    ],
  }));

  const found = await effortsForModels(
    { request } as unknown as AcpConnection,
    { id: "s", settings },
    1_000,
  );

  expect([...found]).toEqual([
    ["plain", []],
    ["deepseek", ["low", "high", "max"]],
    ["other-plain", []],
  ]);
});

it("does not borrow efforts for models whose reply fails or reports another model", async () => {
  const ids = ["a", "failed", "mismatch", "plain"];
  const settings = {
    configOptions: [
      select("model", "model", "a", ids),
      select("effort", "thought_level", "default", ["low", "high", "default"]),
    ],
  };
  const request = vi.fn(async (_: string, { value }: { value: string }) => {
    if (value === "failed") throw new Error("Can't switch");
    if (value === "mismatch") return { configOptions: settings.configOptions };
    return { configOptions: [select("model", "model", value, ids)] };
  });

  const found = await effortsForModels(
    { request } as unknown as AcpConnection,
    { id: "s", settings },
    1_000,
  );

  expect([...found]).toEqual([
    ["a", ["low", "high"]],
    ["plain", []],
  ]);
  expect(modelsOf(settings, found).map(({ efforts }) => efforts)).toEqual([
    ["low", "high"],
    [],
    [],
    [],
  ]);
});

it("returns verified efforts at the deadline without starting another switch", async () => {
  vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "performance"] });
  try {
    const ids = ["a", "deepseek", "slow", "unreached"];
    const settings = {
      configOptions: [
        select("model", "model", "a", ids),
        select("effort", "thought_level", "low", ["low", "high"]),
      ],
    };
    const request = vi.fn(async (_: string, { value }: { value: string }) => {
      if (value === "slow") return new Promise<never>(() => {});
      return {
        configOptions: [
          select("model", "model", value, ids),
          select("effort", "thought_level", "high", ["high", "max"]),
        ],
      };
    });

    const surveyed = effortsForModels(
      { request } as unknown as AcpConnection,
      { id: "s", settings },
      100,
    );
    await vi.advanceTimersByTimeAsync(100);
    const found = await surveyed;

    expect(modelsOf(settings, found).map(({ efforts }) => efforts)).toEqual([
      ["low", "high"],
      ["high", "max"],
      [],
      [],
    ]);
    expect(request.mock.calls.map(([, params]) => params.value)).toEqual([
      "deepseek",
      "slow",
    ]);
  } finally {
    vi.useRealTimers();
  }
});

it("sends the explicit default when resetting effort, even if the session started at another level", async () => {
  const { connection, request } = recording();
  const session = {
    id: "s",
    settings: {
      configOptions: [
        select("effort", "thought_level", "medium", ["low", "medium", "high", "default"]),
      ],
    } as AcpSettings,
  };

  await applySettings(connection, session, [], choice("", "high"));
  await applySettings(connection, session, [], choice());
  await applySettings(connection, session, [], choice());

  expect(request.mock.calls).toEqual([
    ["session/set_config_option", { sessionId: "s", configId: "effort", value: "high" }],
    ["session/set_config_option", { sessionId: "s", configId: "effort", value: "default" }],
  ]);
});

it("takes the first of a wish's names the agent offers, as config option or mode", async () => {
  const wish = [{ option: "mode", value: ["acceptEdits", "auto_edit", "default"] }];
  const asOption = recording();
  await applySettings(
    asOption.connection,
    {
      id: "s",
      settings: {
        configOptions: [select("mode", "mode", "default", ["default", "auto_edit", "yolo"])],
      } as AcpSettings,
    },
    wish,
    choice(),
  );
  expect((asOption.request.mock.calls[0][1] as { value: string }).value).toBe("auto_edit");

  const asMode = recording();
  await applySettings(
    asMode.connection,
    {
      id: "s",
      settings: {
        modes: { currentModeId: "plan", availableModes: [{ id: "plan" }, { id: "default" }] },
      } as AcpSettings,
    },
    wish,
    choice(),
  );
  expect(asMode.request.mock.calls).toEqual([
    ["session/set_mode", { sessionId: "s", modeId: "default" }],
  ]);
});
