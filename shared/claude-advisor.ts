// Claude's advisor: a stronger Claude model the thread's model consults
// mid-turn, server-side. Claude Code calls the setting `advisorModel`.
import { z } from "zod";
import type { AgentModel } from "./agents";

/** No advisor, even when Claude Code's own settings name one. */
export const NO_ADVISOR = "off";

/**
 * A thread's advisor: a model alias like "opus", or `NO_ADVISOR`. Left out,
 * Claude Code's own `advisorModel` setting decides.
 */
export const claudeAdvisorSchema = z.string().trim().min(1).max(200);

/** The models Claude Code's /advisor offers, strongest first. */
const advisorAliases = ["fable", "opus", "sonnet"];

/** What Claude Code's `advisorModel` setting takes for `advisor`; "" turns it off. */
export const advisorModelSetting = (advisor: string) =>
  advisor === NO_ADVISOR ? "" : advisor;

/** The advisors this account's Claude lists, as /advisor offers them. */
export const advisorModels = (models: readonly AgentModel[] | undefined) =>
  advisorAliases.flatMap((alias) => {
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
 * Whether `advisor` could guide `main`. The API wants an advisor at least as
 * capable as the model it guides; Claude Code keeps a weaker one switched
 * off. A model it can't place gets the benefit of the doubt.
 */
export function advisorGuides(advisor: string, main: string) {
  const advisorRank = familyRank(advisor);
  const mainRank = familyRank(main);
  if (advisorRank === undefined || mainRank === undefined) return true;
  return advisorRank >= mainRank;
}
