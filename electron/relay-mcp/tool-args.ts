// Argument shapes several of Relay's tools take (./tools): the ids they name
// threads, projects and schedules by, and a schedule's fields.
import { z } from "zod";
import { agentProviderSchema } from "../../shared/agents";
import { reasoningEffortSchema } from "../../shared/settings";
import { scheduleRepeats } from "../../shared/schedules";

export const threadId = z
  .uuid()
  .describe("A thread id from start_threads, list_threads or find_threads.");

export const projectId = z
  .uuid()
  .describe("A project id from list_projects or add_project.");

export const scheduleId = z
  .uuid()
  .describe("A schedule's id from list_schedules or add_schedule.");

/** Monday first, as the schedule keeps them. */
export const scheduleWeekdays = [
  "monday",
  "tuesday",
  "wednesday",
  "thursday",
  "friday",
  "saturday",
  "sunday",
] as const;

export const scheduleFields = {
  name: z
    .string()
    .trim()
    .min(1)
    .max(120)
    .describe("Short, like “Weekly changelog”."),
  prompt: z
    .string()
    .trim()
    .min(1)
    .max(32000)
    .describe(
      "What Relay sends each time, written as the user would ask it; skills and @files work as in a message. Nobody is around while it runs, so it must not need answers.",
    ),
  repeat: z.enum(scheduleRepeats),
  days: z
    .array(z.enum(scheduleWeekdays))
    .min(1)
    .max(7)
    .optional()
    .describe("For weekly, the days it runs. Left out: Friday."),
  day_of_month: z
    .number()
    .int()
    .min(1)
    .max(31)
    .optional()
    .describe(
      "For monthly; a shorter month runs on its last day. Left out: the 1st.",
    ),
  time: z
    .string()
    .regex(/^([01]\d|2[0-3]):[0-5]\d$/)
    .describe("24-hour time on this computer's clock, like 09:00."),
  project: projectId
    .or(z.literal("scratchpad"))
    .optional()
    .describe(
      "Left out: yours. scratchpad: no project, each run is a Scratchpad chat.",
    ),
  new_chat_each_run: z
    .boolean()
    .optional()
    .describe(
      "Left out: true. False: every run adds a turn to one thread, so it remembers the last run.",
    ),
  worktree: z
    .boolean()
    .optional()
    .describe(
      "Left out: true, each run in a worktree of its own when the project is a Git repository. False: in the project's folder.",
    ),
  agent: agentProviderSchema
    .optional()
    .describe("Left out: you. Check list_agents before naming another."),
  model: z
    .string()
    .trim()
    .max(200)
    .optional()
    .describe("Left out: yours when the agent is you, otherwise its default."),
  effort: reasoningEffortSchema.optional(),
  token_limit: z
    .number()
    .int()
    .min(0)
    .max(10_000_000)
    .optional()
    .describe(
      "Tokens one run may use before Relay stops it; 0 for no limit. Left out: 150000.",
    ),
};
