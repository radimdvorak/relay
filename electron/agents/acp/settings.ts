import type { AgentModel } from "../../../shared/agents";
import {
  reasoningEffortSchema,
  type ModelChoice,
  type ReasoningEffort,
} from "../../../shared/settings";
import { withTimeout } from "../../util/timeout";
import type { AcpConnection } from "./connection";
import type { AcpWish } from "./profiles";
import {
  choicesOf,
  configOptionSchema,
  type AcpConfigOption,
} from "./protocol";
import type { AcpSettings } from "./sessions";

/** Where `base` keeps the model of the older model list, which isn't a config option. */
const legacyModel = "\0model";

const optionIn = (settings: AcpSettings, category: string) =>
  settings.configOptions?.find((o) => o.category === category);

/** Known efforts; an explicit `default` is represented by Relay's Default choice. */
function effortsIn(option: AcpConfigOption | undefined): ReasoningEffort[] {
  if (!option) return [];
  const values = choicesOf(option)
    .map((c) => c.value)
    .filter((v) => v !== "default");
  const efforts = values.flatMap(
    (v) => reasoningEffortSchema.safeParse(v).data ?? [],
  );
  return efforts.length === values.length ? efforts : [];
}

function effortsOf(settings: AcpSettings): ReasoningEffort[] {
  return effortsIn(optionIn(settings, "thought_level"));
}

/** The models a session offers, from its model option or the older model list. */
export function modelsOf(
  settings: AcpSettings,
  effortsByModel?: ReadonlyMap<string, ReasoningEffort[]>,
): AgentModel[] {
  const efforts = effortsOf(settings);
  const option = optionIn(settings, "model");
  const listed = option
    ? choicesOf(option).map((c) => ({
        id: c.value,
        name: c.name,
        description: c.description,
      }))
    : (settings.models?.availableModels ?? []).map((m) => ({
        id: m.modelId,
        name: m.name || m.modelId,
        description: m.description ?? undefined,
      }));
  return listed.map((m) => ({
    id: m.id,
    name: m.name,
    description: (m.description ?? "").slice(0, 300),
    efforts:
      option && effortsByModel ? (effortsByModel.get(m.id) ?? []) : efforts,
  }));
}

/**
 * What each model offers for effort, which ACP tells only for the one a
 * session runs: a probe switches the session through the rest to ask.
 */
export async function effortsForModels(
  connection: AcpConnection,
  session: { id: string; settings: AcpSettings },
  timeoutMs: number,
): Promise<Map<string, ReasoningEffort[]>> {
  const found = new Map<string, ReasoningEffort[]>();
  const model = optionIn(session.settings, "model");
  if (!model) return found;

  // A config_option_update can replace the original options during the survey.
  const originalModel = model.currentValue;
  if (typeof originalModel === "string")
    found.set(originalModel, effortsOf(session.settings));

  const deadline = performance.now() + timeoutMs;
  for (const { value } of choicesOf(model)) {
    if (value === originalModel) continue;
    const remaining = Math.ceil(deadline - performance.now());
    if (remaining <= 0) break;

    try {
      const reply = await withTimeout(
        connection.request<{ configOptions?: unknown } | null>(
          "session/set_config_option",
          {
            sessionId: session.id,
            configId: model.id,
            value,
          },
        ),
        remaining,
        "ACP model effort survey timed out.",
      );
      const configOptions = configOptionSchema
        .array()
        .safeParse(reply?.configOptions).data;
      if (!configOptions) continue;
      const updated = { configOptions };
      if (optionIn(updated, "model")?.currentValue === value)
        found.set(value, effortsOf(updated));
    } catch {
      // Leave this model unverified. A slow switch can spend the whole budget.
    }
  }
  return found;
}

/** The model a session runs and its effort, as it says. */
export function currentOf(settings: AcpSettings) {
  const model = optionIn(settings, "model")?.currentValue ?? settings.models?.currentModelId;
  const effort = reasoningEffortSchema.safeParse(
    optionIn(settings, "thought_level")?.currentValue,
  ).data;
  return {
    model: typeof model === "string" ? model : "",
    effort: effort ?? ("" as const),
  };
}

/**
 * Puts a session's settings where a turn wants them: its approval mode, then
 * its model and effort. Left on Default, a model or effort goes back to what
 * the session started with, or its explicit default effort when offered.
 * Settings the agent doesn't offer are skipped.
 */
export async function applySettings(
  connection: AcpConnection,
  session: { id: string; settings: AcpSettings; base?: Record<string, string> },
  wishes: AcpWish[],
  choice: ModelChoice,
) {
  const sessionId = session.id;
  const { settings } = session;
  const base = (session.base ??= Object.fromEntries([
    ...(settings.configOptions ?? []).flatMap((o) =>
      typeof o.currentValue === "string" ? [[o.id, o.currentValue]] : [],
    ),
    ...(settings.models?.currentModelId
      ? [[legacyModel, settings.models.currentModelId]]
      : []),
  ]));
  const setOption = async (option: AcpConfigOption, value: string) => {
    if (option.currentValue === value) return;
    const reply = (await connection.request("session/set_config_option", {
      sessionId,
      configId: option.id,
      value,
    })) as { configOptions?: unknown } | null;
    const next = configOptionSchema.array().safeParse(reply?.configOptions).data;
    if (next) settings.configOptions = next;
    else option.currentValue = value;
  };
  const offers = (option: AcpConfigOption, value: string) =>
    choicesOf(option).some((c) => c.value === value);

  for (const wish of wishes) {
    // The first value the agent offers, by config option or else by mode.
    const values = typeof wish.value === "string" ? [wish.value] : wish.value;
    const found = values
      .map((value) => ({
        value,
        option: settings.configOptions?.find(
          (o) => (o.id === wish.option || o.category === wish.option) && offers(o, value),
        ),
      }))
      .find((f) => f.option);
    if (found?.option) {
      await setOption(found.option, found.value);
      continue;
    }
    const modes = settings.modes;
    const mode =
      wish.option === "mode" &&
      values.find((value) => modes?.availableModes.some((m) => m.id === value));
    if (modes && mode && modes.currentModeId !== mode) {
      await connection.request("session/set_mode", { sessionId, modeId: mode });
      modes.currentModeId = mode;
    }
  }

  const modelOption = optionIn(settings, "model");
  const model = choice.model || (modelOption ? base[modelOption.id] : undefined);
  if (modelOption && model && offers(modelOption, model)) await setOption(modelOption, model);
  else if (!modelOption && settings.models) {
    const models = settings.models;
    const wanted = choice.model || base[legacyModel];
    if (wanted && models.currentModelId !== wanted && models.availableModels.some((m) => m.modelId === wanted)) {
      await connection.request("session/set_model", { sessionId, modelId: wanted });
      models.currentModelId = wanted;
    }
  }

  // Read again: a model change can bring other effort levels.
  const effortOption = optionIn(settings, "thought_level");
  const defaultEffort = effortOption
    ? offers(effortOption, "default")
      ? "default"
      : base[effortOption.id]
    : undefined;
  const effort = choice.reasoningEffort || defaultEffort;
  if (effortOption && effort && offers(effortOption, effort)) await setOption(effortOption, effort);
}
