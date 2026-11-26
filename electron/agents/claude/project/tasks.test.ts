import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { expect, it, vi } from "vitest";
import type { AgentActivity } from "../../../../shared/projects";
import type { ClaudeRunOptions } from "./config";
import { ClaudeTasks, savedTasks } from "./tasks";
import { ToolRows } from "./tool-rows";

const created = (id: string, subject: string) => ({ task: { id, subject } });
const ok = { success: true };

const make = (tasks: ClaudeTasks, id: string, subject: string) =>
  tasks.after("TaskCreate", { subject, description: "" }, created(id, subject));

const move = (tasks: ClaudeTasks, taskId: string, status: string, output: unknown = ok) =>
  tasks.after("TaskUpdate", { taskId, status }, output);

it("follows a checklist one task at a time", () => {
  const tasks = new ClaudeTasks();
  make(tasks, "1", "Build");
  make(tasks, "2", "Test");
  move(tasks, "1", "completed");
  expect(move(tasks, "2", "in_progress")).toEqual([
    { text: "Build", state: "done" },
    { text: "Test", state: "now" },
  ]);
  expect(tasks.after("TaskUpdate", { taskId: "2", subject: "Test it" }, ok)?.[1]).toEqual({ text: "Test it", state: "now" });
  expect(move(tasks, "2", "deleted")).toEqual([{ text: "Build", state: "done" }]);
});

it("changes nothing for a task Claude Code didn't find, or one it can't read", () => {
  const tasks = new ClaudeTasks();
  make(tasks, "1", "Build");
  expect(move(tasks, "9", "completed", { success: false, error: "Task not found" })).toBeUndefined();
  expect(move(tasks, "9", "completed")).toBeUndefined();
  expect(tasks.after("TaskCreate", {}, "Error")).toBeUndefined();
});

it("starts over in a turn after the checklist is done, and adds to one that isn't", () => {
  const tasks = new ClaudeTasks();
  make(tasks, "1", "Build");
  tasks.beginTurn();
  expect(make(tasks, "2", "Test")).toHaveLength(2);
  move(tasks, "1", "completed");
  move(tasks, "2", "completed");
  tasks.beginTurn();
  expect(make(tasks, "3", "Ship")).toEqual([{ text: "Ship", state: "next" }]);
  // Done within the turn, a task stays with the ones made after it.
  move(tasks, "3", "completed");
  expect(make(tasks, "4", "Announce")).toHaveLength(2);
  // So does one the turn finished before making the next.
  tasks.beginTurn();
  move(tasks, "4", "completed");
  expect(make(tasks, "5", "Celebrate")).toHaveLength(3);
});

it("picks a resumed session's checklist up from what Claude Code saved", async () => {
  const home = await mkdtemp(join(tmpdir(), "relay-tasks-"));
  const dir = join(home, "tasks", "session");
  await mkdir(dir, { recursive: true });
  const save = (id: string, subject: string, status: string) =>
    writeFile(join(dir, `${id}.json`), JSON.stringify({ id, subject, status, blocks: [] }));
  await save("10", "Ship", "pending");
  await save("2", "Build", "completed");
  await writeFile(join(dir, ".lock"), "");
  // Claude Code halfway through writing one leaves the rest readable.
  await writeFile(join(dir, "11.json"), '{"id":"11","sub');
  const tasks = new ClaudeTasks(() => savedTasks(home, "session"));
  expect(move(tasks, "10", "in_progress")).toEqual([
    { text: "Build", state: "done" },
    { text: "Ship", state: "now" },
  ]);
  expect(savedTasks(home, "another")).toEqual([]);
  await rm(home, { recursive: true });
});

it("shows a turn's changes as one row, and a subagent's as its own calls", () => {
  const onActivity = vi.fn<(a: AgentActivity) => void>();
  const rows = new ToolRows({ onActivity } as unknown as ClaudeRunOptions);
  const result = (id: string, returned: unknown, parent: string | null = null) =>
    rows.results([{ type: "tool_result", tool_use_id: id, content: "" }], parent, returned);
  rows.call({ id: "a", name: "TaskCreate", input: { subject: "Build" } }, null);
  expect(onActivity).not.toHaveBeenCalled();
  result("a", created("1", "Build"));
  rows.call({ id: "b", name: "TaskUpdate", input: { taskId: "1", status: "in_progress" } }, null);
  result("b", ok);
  expect(onActivity.mock.calls.map(([a]) => [a.id, a.detail])).toEqual([
    ["checklist:a", "○ Build"],
    ["checklist:a", "→ Build"],
  ]);
  rows.call({ id: "c", name: "TaskCreate", input: { subject: "Dig" } }, "agent-call");
  const last = onActivity.mock.lastCall![0];
  expect(last).toMatchObject({ id: "c", label: "Updated progress", parentId: "agent-call" });
  expect(last.checklist).toBeUndefined();
});

it("shows a change that didn't go through as its own call, and leaves the checklist alone", () => {
  const onActivity = vi.fn<(a: AgentActivity) => void>();
  const rows = new ToolRows({ onActivity } as unknown as ClaudeRunOptions);
  const result = (id: string, returned: unknown, is_error = false) =>
    rows.results([{ type: "tool_result", tool_use_id: id, content: is_error ? "Denied" : "", is_error }], null, returned);
  rows.call({ id: "a", name: "TaskUpdate", input: { taskId: "9", status: "completed" } }, null);
  result("a", { success: false, error: "Task not found" });
  const todos = [{ content: "Build", status: "completed" }];
  rows.call({ id: "b", name: "TodoWrite", input: { todos } }, null);
  result("b", undefined, true);
  expect(onActivity.mock.calls.map(([a]) => [a.id, a.status, a.checklist, a.detail])).toEqual([
    ["a", "failed", undefined, undefined],
    ["b", "failed", undefined, "Denied"],
  ]);
});

it("reads the older to-do list whole", () => {
  const todos = [
    { content: "Build", status: "completed", activeForm: "Building" },
    { content: "Test", status: "pending", activeForm: "Testing" },
  ];
  expect(new ClaudeTasks().after("TodoWrite", { todos }, {})).toEqual([
    { text: "Build", state: "done" },
    { text: "Test", state: "next" },
  ]);
});
