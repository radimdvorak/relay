// The tools Relay offers an agent for starting and driving threads, its own
// and, once the user lets it, any other, for the notes the user keeps in them,
// for adding the projects they work in, for reading its plans' usage limits,
// for looking at its thread's preview (electron/preview/agent-tools), and for
// showing pages in its answer (electron/html-renders).
// What each tells the agent is in ./listed-tools. The agent host lists them
// without asking Relay, so they stay put while it restarts; Relay answers the
// calls (electron/started-threads).
import { z } from "zod";
import { agentProviderSchema, agentProviders } from "../../shared/agents";
import { reasoningEffortSchema } from "../../shared/settings";
import { noteIdSchema, noteTextSchema } from "../../shared/thread-notes";
import {
  REVIEW_SETUP_NAME_MAX,
  reviewTargetSchema,
} from "../../shared/deep-review";
import { RENDER_MAX_CHARS, RENDER_MAX_PAGES } from "../../shared/html-render";
import { secretRequestSchema } from "../../shared/secret-request";
import { projectId, scheduleFields, scheduleId, threadId } from "./tool-args";

/** How many threads one thread may have working at once. */
export const STARTED_LIMIT = 6;
/** How much of one message read_thread returns; `offset` reads on. */
export const MESSAGE_CHARS = 20_000;
/** The most threads find_threads returns. */
const FIND_LIMIT = 50;
/** The longest wait_for_threads holds its call. */
export const WAIT_LIMIT_SECONDS = 600;
/** How many times one turn may ask its advisor. */
export const ADVISOR_CALLS_PER_TURN = 3;
/** The longest question ask_advisor takes. */
const ADVISOR_QUESTION_CHARS = 8_000;
/** The furthest ahead wake_this_thread sets a wake-up: a week. */
const WAKE_LIMIT_MINUTES = 7 * 24 * 60;

const pageHtml = z
  .string()
  .min(1)
  .max(RENDER_MAX_CHARS)
  .describe("A whole self-contained HTML document.");

export const relayToolSchemas = {
  start_threads: z
    .object({
      project: projectId
        .optional()
        .describe(
          "The project they work in, from list_projects or add_project. Left out: yours. Another project needs the user's leave to drive threads.",
        ),
      detached: z
        .boolean()
        .optional()
        .describe(
          "Start them as threads of their own instead of under yours: they aren't in your list_threads and don't count toward your working limit. Needs the user's leave to drive threads.",
        ),
      threads: z
        .array(
          z
            .object({
              prompt: z
                .string()
                .trim()
                .min(1)
                .max(32000)
                .describe(
                  "The whole task, self-contained: the new thread sees none of this conversation.",
                ),
              agent: agentProviderSchema
                .optional()
                .describe(
                  `Who works on it: ${agentProviders.join(", ")}, or acp:<id> for an agent the user installed from the ACP registry. Only one list_agents says can run here. A model's name is not an agent; it goes in \`model\`. Left out: the agent you are.`,
                ),
              model: z
                .string()
                .max(120)
                .optional()
                .describe(
                  "A model id that agent offers, as list_agents gives it. Left out: yours when it's the same agent, otherwise that agent's default.",
                ),
              effort: reasoningEffortSchema
                .optional()
                .describe("Reasoning effort. Left out: as with model."),
              fast: z
                .boolean()
                .optional()
                .describe(
                  "Fast mode on or off, on a model that supports it (list_agents says which). Left out: inherit yours on the same agent, otherwise Standard.",
                ),
              worktree: z
                .boolean()
                .optional()
                .describe(
                  "Work in a worktree of its own on a new branch (the default, except read only), so threads working at once don't edit the same files. False: the project's checkout.",
                ),
              uncommitted: z
                .boolean()
                .optional()
                .describe(
                  "Its worktree starts from the files you work on, your uncommitted edits included (the default, in your own project only). False: from your last commit only.",
                ),
              plan: z
                .boolean()
                .optional()
                .describe(
                  "Plan first and wait for the user's go-ahead before changing anything.",
                ),
              readOnly: z
                .boolean()
                .optional()
                .describe(
                  "Read only: it reads and runs read-only commands but can't change files, and nobody answers its questions. For reviews, research and second opinions. It works in the project's checkout unless `worktree` says otherwise. Left out: the access you have.",
                ),
            })
            .strict(),
        )
        .min(1)
        .max(STARTED_LIMIT),
    })
    .strict(),
  list_threads: z.object({}).strict(),
  find_threads: z
    .object({
      project: projectId
        .optional()
        .describe("Only this project's threads. Left out: every project's."),
      query: z
        .string()
        .trim()
        .min(1)
        .max(200)
        .optional()
        .describe("Words that must all be in the title or branch name."),
      limit: z
        .number()
        .int()
        .min(1)
        .max(FIND_LIMIT)
        .optional()
        .describe("How many, newest first. Default 20."),
    })
    .strict(),
  read_thread: z
    .object({
      id: threadId,
      after: z
        .string()
        .optional()
        .describe(
          "Only messages after this message id, the `next` of an earlier read.",
        ),
      before: z
        .string()
        .optional()
        .describe(
          "Only messages before this message id, the `earlier` of a read.",
        ),
      limit: z
        .number()
        .int()
        .min(1)
        .max(100)
        .optional()
        .describe("At most this many messages. Default 50."),
      message: z
        .string()
        .optional()
        .describe(
          "Read on in one long message: its id, with `offset` its `nextOffset`.",
        ),
      offset: z.number().int().min(0).optional(),
    })
    .strict(),
  send_to_thread: z
    .object({
      id: threadId,
      message: z.string().trim().min(1).max(32000),
      steer: z
        .boolean()
        .optional()
        .describe(
          "true: steer, it reaches the agent mid-answer. Left out: queue, it waits for the answer to end.",
        ),
    })
    .strict(),
  wait_for_threads: z
    .object({
      ids: z
        .array(threadId)
        .min(1)
        .max(50)
        .optional()
        .describe("Any threads. Left out: every thread you started."),
      timeoutSeconds: z
        .number()
        .int()
        .min(1)
        .max(WAIT_LIMIT_SECONDS)
        .optional()
        .describe(`At most ${WAIT_LIMIT_SECONDS}; default 300.`),
    })
    .strict(),
  stop_thread: z.object({ id: threadId }).strict(),
  message_lead: z
    .object({ message: z.string().trim().min(1).max(32000) })
    .strict(),
  message_main: z
    .object({ message: z.string().trim().min(1).max(32000) })
    .strict(),
  hand_off_thread: z
    .object({
      id: threadId,
      to: z
        .string()
        .trim()
        .min(1)
        .max(120)
        .optional()
        .describe(
          'The paired computer\'s name, or "here" to bring the thread back. Left out: the one computer that can take it.',
        ),
    })
    .strict(),
  settle_thread: z.object({ id: threadId }).strict(),
  settle_this_thread: z.object({}).strict(),
  ask_advisor: z
    .object({ question: z.string().trim().min(1).max(ADVISOR_QUESTION_CHARS) })
    .strict(),
  wake_this_thread: z
    .object({
      minutes: z
        .number()
        .int()
        .min(1)
        .max(WAKE_LIMIT_MINUTES)
        .describe("How long from now, in minutes."),
      prompt: z
        .string()
        .trim()
        .min(1)
        .max(4000)
        .describe(
          "Your next message, written to yourself: what to check then, and what to do with what you find.",
        ),
    })
    .strict(),
  usage_limits: z.object({}).strict(),
  list_agents: z
    .object({
      agent: agentProviderSchema
        .optional()
        .describe(
          "Only this agent, with all its models. Left out: every agent.",
        ),
    })
    .strict(),
  list_notes: z
    .object({
      thread: threadId
        .optional()
        .describe("Whose notes. Left out: this thread's."),
    })
    .strict(),
  add_note: z
    .object({
      text: noteTextSchema.describe(
        "Markdown. A list, with optional lines leading into it and nothing after, becomes items the user ticks off.",
      ),
      thread: threadId
        .optional()
        .describe(
          "Whose notes. Left out: this thread's. Another thread's asks the user first, unless you started it.",
        ),
    })
    .strict(),
  tick_note: z
    .object({
      note: noteIdSchema.describe("The note's id from list_notes, like n2."),
      item: z
        .number()
        .int()
        .min(1)
        .describe("The item's number in list_notes, from 1."),
      done: z
        .boolean()
        .optional()
        .describe("False unticks it. Left out: ticks it."),
      thread: threadId
        .optional()
        .describe(
          "Whose notes. Left out: this thread's. Another thread's asks the user first, unless you started it.",
        ),
    })
    .strict(),
  move_to_worktree: z
    .object({
      branch: z
        .string()
        .trim()
        .min(1)
        .max(200)
        .optional()
        .describe(
          "The branch to work on. An existing one joins the worktree it's checked out in, which another thread or the user may be working in, or gets one of its own. A new name makes that branch. Left out: Relay names a new one after the thread, relay/….",
        ),
      uncommitted: z
        .boolean()
        .optional()
        .describe(
          "Start a new branch's worktree with a copy of the project folder's uncommitted edits, which stay in the folder too. Not for an existing branch. Left out: from the last commit only.",
        ),
    })
    .strict(),
  deep_review: z
    .object({
      target: reviewTargetSchema
        .optional()
        .describe(
          "What to review: uncommitted changes, a branch against its base, a commit, or a pull request. Left out: this thread's uncommitted changes.",
        ),
      setup: z
        .string()
        .trim()
        .min(1)
        .max(REVIEW_SETUP_NAME_MAX)
        .optional()
        .describe(
          "A saved review setup's name, as list_review_setups gives it. Left out: the one the user ran last.",
        ),
      focus: z
        .string()
        .trim()
        .max(4000)
        .optional()
        .describe("What the reviewers should look at hardest."),
    })
    .strict(),
  list_review_setups: z.object({}).strict(),
  list_schedules: z.object({}).strict(),
  add_schedule: z.object(scheduleFields).strict(),
  update_schedule: z
    .object({
      id: scheduleId,
      ...z.object(scheduleFields).partial().shape,
    })
    .strict(),
  pause_schedule: z
    .object({
      id: scheduleId,
      paused: z.boolean().describe("False turns it back on."),
    })
    .strict(),
  remove_schedule: z.object({ id: scheduleId }).strict(),
  run_schedule: z.object({ id: scheduleId }).strict(),
  list_projects: z.object({}).strict(),
  move_to_project: z.object({ project: projectId }).strict(),
  add_project: z
    .object({
      folder: z
        .string()
        .trim()
        .min(1)
        .max(4096)
        .describe("The folder's absolute path."),
    })
    .strict(),
  open_preview: z
    .object({
      url: z
        .string()
        .trim()
        .min(1)
        .max(8192)
        .optional()
        .describe(
          "What to load: a full http(s) URL, or a path like /settings on the page's origin (the dev server's when nothing is loaded yet). Left out: the page it shows, or the dev server's home.",
        ),
      reload: z
        .boolean()
        .optional()
        .describe("Reload the page it shows, when no url is given."),
      command: z
        .string()
        .trim()
        .min(1)
        .max(8192)
        .optional()
        .describe(
          "Save the dev command for this thread's localhost preview, e.g. npm run dev -- --port 5173. Relay runs it in the preview's folder when its server is gone, including after quitting. Supply it when you start a preview server; use a command that stays in the foreground and listens on the URL's port (also passed as PORT).",
        ),
    })
    .strict(),
  screenshot: z.object({}).strict(),
  console_errors: z
    .object({
      clear: z
        .boolean()
        .optional()
        .describe(
          "Empty the list after reading it, so the next call shows only what's new.",
        ),
    })
    .strict(),
  show_html: z
    .object({
      title: z
        .string()
        .trim()
        .min(1)
        .max(120)
        .describe("What it shows, in a few words; it heads the page."),
      html: pageHtml.optional().describe("One page. Give this or variants."),
      variants: z
        .array(
          z
            .object({
              label: z
                .string()
                .trim()
                .min(1)
                .max(60)
                .describe("A short name for this alternative."),
              html: pageHtml,
            })
            .strict(),
        )
        .min(2)
        .max(RENDER_MAX_PAGES)
        .optional()
        .describe(
          "Alternatives to compare, each a whole page, shown as tabs the user switches between.",
        ),
    })
    .strict(),
  ask_html: z
    .object({
      title: z
        .string()
        .trim()
        .min(1)
        .max(120)
        .describe("What you ask, in a few words; it heads the card."),
      html: pageHtml.describe(
        "The page the user answers in. It calls window.relay.answer(value) as their choice changes.",
      ),
      detail: z
        .string()
        .trim()
        .max(2000)
        .optional()
        .describe("Why you ask, if the page doesn't say; shown over it."),
    })
    .strict(),
  preview_html: z
    .object({
      html: pageHtml,
      width: z
        .number()
        .int()
        .min(320)
        .max(1200)
        .optional()
        .describe("Frame width in px. Default 800."),
    })
    .strict(),
  request_secret: secretRequestSchema,
  import_sources: z
    .object({
      project: z
        .string()
        .min(1)
        .max(100)
        .optional()
        .describe("A project's key: list its chats. Left out: every project."),
    })
    .strict(),
  offer_import: z
    .object({
      projects: z
        .array(
          z
            .object({
              key: z.string().min(1).max(100),
              group: z
                .string()
                .trim()
                .max(400)
                .optional()
                .describe(
                  'Its sidebar group; "Work/Clients" nests. Left out: none.',
                ),
              chats: z
                .array(z.string().min(1).max(200))
                .max(10000)
                .optional()
                .describe(
                  "Chat ids to tick. Left out: all it doesn't have yet.",
                ),
            })
            .strict(),
        )
        .min(1)
        .max(1000)
        .describe("In the order they go in the sidebar."),
    })
    .strict(),
  restart_relay: z
    .object({
      reason: z
        .string()
        .trim()
        .min(1)
        .max(500)
        .describe(
          "What changed that the running Relay doesn't have yet, in a sentence; the user reads it on the card.",
        ),
    })
    .strict(),
  report_relay_problem: z
    .object({
      kind: z
        .enum(["bug", "feature"])
        .optional()
        .describe("A bug, or a feature request. Left out: bug."),
      title: z
        .string()
        .trim()
        .min(1)
        .max(200)
        .describe("The issue's title: what goes wrong, in a line."),
      description: z
        .string()
        .trim()
        .min(1)
        .max(20000)
        .describe(
          "GitHub Markdown. For a bug: what the user did, what happened, what they expected, steps to reproduce. For a feature: what they'd like and what it helps them do. Leave out Relay's version, the system and the log: the report adds those itself.",
        ),
    })
    .strict(),
};

export type RelayToolName = keyof typeof relayToolSchemas;
export type RelayToolArgs<N extends RelayToolName> = z.infer<
  (typeof relayToolSchemas)[N]
>;

export { resultText, toolText, type ToolResult } from "./tool-result";
