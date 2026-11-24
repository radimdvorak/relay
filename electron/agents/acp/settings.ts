import type { AgentModel } from "../../../shared/agents";
import {
  reasoningEffortSchema,
  type ModelChoice,
  type ReasoningEffort,
} from "../../../shared/settings";
import { withTimeout } from "../../util/timeout";
import { AcpRequestError, type AcpConnection } from "./connection";
import type { AcpWish } from "./profiles";
import {
  choicesOf,
  configOptionSchema,
  parseUpdate,
  type AcpConfigOption,
} from "./protocol";
import type { AcpSettings } from "./sessions";

/** Where `base` keeps the model of the older model list, which isn't a config option. */
const legacyModel = "\0model";

/** JSON-RPC "Method not found", for agents that don't implement `session/set_config_option`. */
const METHOD_NOT_FOUND = -32601;

/** Cap for a single model switch so one slow model doesn't spend the whole survey budget. */
const defaultPerModelTimeout = 1_500;

const optionIn = (settings: AcpSettings, category: string) =>
  settings.configOptions?.find((o) => o.category === category);

/** Known efforts; an explicit `default` is represented by Relay's Default choice. */
function effortsOf(settings: AcpSettings): ReasoningEffort[] {
  const option = optionIn(settings, "thought_level");
  if (!option) return [];
  const values = choicesOf(option)
    .map((c) => c.value)
    .filter((v) => v !== "default");
  const efforts = values.flatMap(
    (v) => reasoningEffortSchema.safeParse(v).data ?? [],
  );
  return efforts.length === values.length ? efforts : [];
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

/** Switches a session's model config option within `timeoutMs`, returning its updated options. */
async function setModelOption(
  connection: AcpConnection,
  sessionId: string,
  configId: string,
  value: string,
  timeoutMs: number,
) {
  const reply = await withTimeout(
    connection.request<{ configOptions?: unknown } | null>(
      "session/set_config_option",
      { sessionId, configId, value },
    ),
    timeoutMs,
    "ACP model effort survey timed out.",
  );
  return configOptionSchema.array().safeParse(reply?.configOptions).data;
}

/** Distinct model ids to survey, skipping the active one and any filtered out. */
function surveyCandidates(
  model: AcpConfigOption,
  originalModel: unknown,
  only?: ReadonlySet<string>,
): string[] {
  const seen = new Set<string>();
  return choicesOf(model).flatMap(({ value }) => {
    if (value === originalModel || seen.has(value)) return [];
    if (only && !only.has(value)) return [];
    seen.add(value);
    return [value];
  });
}

/** Tracks the latest `config_option_update` on `sessionId`, for agents whose switch reply omits options. */
function watchConfigOptions(connection: AcpConnection, sessionId: string) {
  let latest: AcpConfigOption[] | undefined;
  connection.listen?.(sessionId, (raw) => {
    const update = parseUpdate(raw);
    if (update?.sessionUpdate === "config_option_update")
      latest = update.configOptions;
  });
  return {
    reset: () => {
      latest = undefined;
    },
    latest: () => latest,
    stop: () => connection.unlisten?.(sessionId),
  };
}

/**
 * What each model offers for effort, which ACP tells only for the one a
 * session runs: a probe switches the session through the rest to ask, then
 * puts it back. Returns `undefined` when the agent doesn't report options on
 * switches so callers keep the session's initial efforts.
 */
export async function effortsForModels(
  connection: AcpConnection,
  session: { id: string; settings: AcpSettings },
  timeoutMs: number,
  {
    perModelMs = defaultPerModelTimeout,
    only,
  }: { perModelMs?: number; only?: ReadonlySet<string> } = {},
): Promise<Map<string, ReasoningEffort[]> | undefined> {
  const found = new Map<string, ReasoningEffort[]>();
  const model = optionIn(session.settings, "model");
  if (!model) return found;

  // A config_option_update can replace the original options during the survey.
  const originalModel = model.currentValue;
  if (typeof originalModel === "string")
    found.set(originalModel, effortsOf(session.settings));

  const deadline = performance.now() + timeoutMs;
  const remainingMs = () => Math.ceil(deadline - performance.now());
  const updates = watchConfigOptions(connection, session.id);
  const switchTo = (value: string, cap: number) =>
    setModelOption(connection, session.id, model.id, value, cap);
  let switched = false;
  let sawOptions = false;
  let sawEmptyReply = false;
  let sawFailure = false;
  let unsupported = false;

  try {
    for (const value of surveyCandidates(model, originalModel, only)) {
      const remaining = remainingMs();
      if (connection.closed || remaining <= 0) break;

      updates.reset();
      switched = true;
      try {
        const cap = Math.min(remaining, perModelMs);
        const configOptions = (await switchTo(value, cap)) ?? updates.latest();
        if (!configOptions) {
          sawEmptyReply = true;
          continue;
        }
        sawOptions = true;
        const updated = { configOptions };
        if (optionIn(updated, "model")?.currentValue === value)
          found.set(value, effortsOf(updated));
      } catch (error) {
        if (
          error instanceof AcpRequestError &&
          error.code === METHOD_NOT_FOUND
        ) {
          unsupported = true;
          break;
        }
        sawFailure = true;
        if (connection.closed) break;
      }
    }
  } finally {
    updates.stop();
    const remaining = remainingMs();
    const shouldRestore =
      switched &&
      !unsupported &&
      typeof originalModel === "string" &&
      !connection.closed &&
      remaining > 0;
    if (shouldRestore)
      await switchTo(originalModel, Math.min(remaining, perModelMs)).catch(
        () => {},
      );
  }

  const cannotInspect =
    unsupported || (sawEmptyReply && !sawOptions && !sawFailure);
  return cannotInspect ? undefined : found;
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
