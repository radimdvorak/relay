// A thread's advisor: a stronger model its agent consults mid-turn. Claude
// asks a Claude advisor itself, server-side (Claude Code calls the setting
// `advisorModel`); every other pair goes through Relay's ask_advisor tool.
import { z } from "zod";
import {
  agentProviderSchema,
  type AgentModel,
  type AgentProvider,
} from "./agents";

/** No advisor, even when Claude Code's own settings name one. */
export const NO_ADVISOR = "off";

export const advisorSchema = z
  .object({
    provider: agentProviderSchema,
    model: z.string().trim().min(1).max(200),
  })
  .strict();

export type Advisor = z.infer<typeof advisorSchema>;

/**
 * An agent's advisor in a thread, or NO_ADVISOR. Left out, Claude Code's own
 * `advisorModel` setting decides for Claude, and other agents have none.
 */
export const threadAdvisorSchema = z.union([
  z.literal(NO_ADVISOR),
  advisorSchema,
]);

export type ThreadAdvisor = z.infer<typeof threadAdvisorSchema>;

export function sameAdvisor(a?: ThreadAdvisor, b?: ThreadAdvisor) {
  if (typeof a !== "object" || typeof b !== "object") return a === b;
  return a.provider === b.provider && a.model === b.model;
}

/** The models Claude Code's /advisor offers, strongest first. */
const claudeAdvisorAliases = ["fable", "opus", "sonnet"];

/** The Claude advisors this account's Claude lists, as Claude Code's /advisor offers them. */
export const claudeAdvisorModels = (
  models: readonly AgentModel[] | undefined,
) =>
  claudeAdvisorAliases.flatMap((alias) => {
    const listed = models?.find((m) => m.id === alias);
    return listed ? [listed] : [];
  });

const familyRank = (model: string) => {
  const name = model.toLowerCase();
  if (/fable|mythos/.test(name)) return 3;
  if (name.includes("opus")) return 2;
  if (name.includes("sonnet")) return 1;
  if (name.includes("haiku")) return 0;
  return undefined;
};

/**
 * Whether Claude's own advisor `advisor` could guide Claude on `main`. The API
 * wants an advisor at least as capable as the model it guides; Claude Code
 * keeps a weaker one switched off. A model it can't place gets the benefit of
 * the doubt.
 */
export function advisorGuides(advisor: string, main: string) {
  const advisorRank = familyRank(advisor);
  const mainRank = familyRank(main);
  if (advisorRank === undefined || mainRank === undefined) return true;
  return advisorRank >= mainRank;
}

/**
 * Whether `advisor` brings something `provider` running `model` lacks. The
 * same model only rereads the thread at full price, and a weaker Claude
 * shares a stronger one's blind spots; another vendor's model is worth
 * hearing at any strength.
 */
export function advisorWorthAsking(
  advisor: Advisor,
  provider: AgentProvider,
  model: string,
) {
  const sameModel = advisor.provider === provider && advisor.model === model;
  if (sameModel) return false;
  const advisorRank = familyRank(advisor.model);
  const mainRank = familyRank(model);
  if (advisorRank === undefined || mainRank === undefined) return true;
  return advisorRank > mainRank;
}

/** How a thread's agent hears its advisor: Claude asks it itself, or Relay does. */
export type AdvisorRoute =
  | { via: "claude"; model: string }
  | { via: "relay"; advisor: Advisor };

/** How `provider` running `model` hears `advisor`; none when it has none. */
export function advisorRoute(
  provider: string,
  model: string,
  advisor: ThreadAdvisor | undefined,
): AdvisorRoute | undefined {
  if (!advisor || advisor === NO_ADVISOR) return undefined;
  const claudeAsksItself =
    provider === "claude" &&
    advisor.provider === "claude" &&
    advisorGuides(advisor.model, model);
  if (claudeAsksItself) return { via: "claude", model: advisor.model };
  return { via: "relay", advisor };
}

/**
 * What Claude Code's `advisorModel` setting takes for a thread on `model`:
 * its own advisor, or "" for none, which an advisor Relay asks counts as.
 * Undefined leaves Claude Code's own setting.
 */
export function claudeAdvisorSetting(
  advisor: ThreadAdvisor | undefined,
  model: string,
): string | undefined {
  if (advisor === undefined) return undefined;
  const route = advisorRoute("claude", model, advisor);
  return route?.via === "claude" ? route.model : "";
}
