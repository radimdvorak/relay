// The checklist Claude keeps with its own task tools, followed across a session's
// turns. A call names one task by id, so the rest has to be remembered.
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { z } from "zod";
import { keptSteps, type ChecklistStep } from "../../../../shared/agent-checklist";

const taskTools = ["TaskCreate", "TaskUpdate", "TodoWrite"] as const;
type TaskTool = (typeof taskTools)[number];

/** A call Claude changes its checklist with. */
export const isTaskTool = (name: string): name is TaskTool =>
  (taskTools as readonly string[]).includes(name);

const statusSchema = z.enum(["pending", "in_progress", "completed"]);

const savedSchema = z.object({
  id: z.string(),
  subject: z.string(),
  status: statusSchema,
});

export type SavedTask = z.infer<typeof savedSchema>;

const createdSchema = z.object({
  task: z.object({ id: z.string(), subject: z.string() }),
});

const updateSchema = z.object({
  taskId: z.string(),
  subject: z.string().optional(),
  status: statusSchema.or(z.literal("deleted")).optional(),
});

const updatedSchema = z.object({ success: z.literal(true) });

const todosSchema = z.object({
  todos: z.array(z.object({ content: z.string(), status: statusSchema })),
});

const states: Record<z.infer<typeof statusSchema>, ChecklistStep["state"]> = {
  pending: "next",
  in_progress: "now",
  completed: "done",
};

const byId = (a: SavedTask, b: SavedTask) => Number(a.id) - Number(b.id);

/**
 * The tasks Claude Code saved for a session. Read in one go: they're a few
 * small files, asked for once, when a session that was resumed first changes
 * its checklist. None when there are none, or another version keeps them elsewhere.
 */
export function savedTasks(home: string, sessionId: string): SavedTask[] {
  const dir = join(home, "tasks", sessionId);
  try {
    return readdirSync(dir)
      .filter((name) => /^\d+\.json$/.test(name))
      .flatMap((name) => {
        const text = readFileSync(join(dir, name), "utf8");
        const task = savedSchema.safeParse(JSON.parse(text));
        return task.success ? [task.data] : [];
      })
      .sort(byId);
  } catch {
    return [];
  }
}

export class ClaudeTasks {
  private steps = new Map<string, ChecklistStep>();
  private restored = false;
  private startsTurn = true;

  /** `saved` is what Claude Code has on disk for the session. */
  constructor(private saved: () => SavedTask[] = () => []) {}

  /** A task made in a new turn starts a new checklist, if the last one was over before the turn touched it. */
  beginTurn() {
    this.startsTurn = true;
  }

  /** The checklist once a call came back, or undefined when the call changed nothing. */
  after(name: TaskTool, input: unknown, output: unknown) {
    if (name === "TodoWrite") return this.written(input);
    this.restore();
    const changed =
      name === "TaskCreate" ? this.created(output) : this.updated(input, output);
    return changed ? (keptSteps([...this.steps.values()]) ?? []) : undefined;
  }

  private written(input: unknown) {
    const list = todosSchema.safeParse(input);
    if (!list.success) return undefined;
    return (
      keptSteps(
        list.data.todos.map((todo) => ({
          text: todo.content,
          state: states[todo.status],
        })),
      ) ?? []
    );
  }

  private created(output: unknown) {
    const made = createdSchema.safeParse(output);
    if (!made.success) return false;
    const { id, subject } = made.data.task;
    if (this.startsTurn) this.dropFinishedChecklist(id);
    this.startsTurn = false;
    // Read off the disk, it may already be further along.
    const known = this.steps.get(id);
    this.steps.set(id, { text: subject, state: known?.state ?? "next" });
    return true;
  }

  private updated(input: unknown, output: unknown) {
    const asked = updateSchema.safeParse(input);
    if (!asked.success || !updatedSchema.safeParse(output).success) return false;
    const { taskId, subject, status } = asked.data;
    const step = this.steps.get(taskId);
    if (!step) return false;
    // The turn has taken the checklist up, so a task it makes next joins it.
    this.startsTurn = false;
    if (status === "deleted") return this.steps.delete(taskId);
    this.steps.set(taskId, {
      text: subject ?? step.text,
      state: status ? states[status] : step.state,
    });
    return true;
  }

  /** Drops the tasks made before `id`, when every one of them is done. */
  private dropFinishedChecklist(id: string) {
    const before = [...this.steps.keys()].filter((key) => Number(key) < Number(id));
    const finished = before.every((key) => this.steps.get(key)!.state === "done");
    if (finished) for (const key of before) this.steps.delete(key);
  }

  private restore() {
    if (this.restored) return;
    this.restored = true;
    for (const task of this.saved())
      this.steps.set(task.id, { text: task.subject, state: states[task.status] });
  }
}
