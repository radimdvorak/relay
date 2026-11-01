import { afterEach, beforeEach, expect, it } from "vitest";
import { rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { TurnSnapshot } from "./turn-snapshot";
import {
  claudeActivity,
  claudeCommand,
  codexActivity,
  codexCommand,
} from "../agents/activity";
import { museActivity, museCommand } from "../agents/muse/activity";
import { gitIn, tempRepo } from "../../tests/fixtures/git-repo";
import type { AgentActivity, ChatMessage, ProjectChat } from "../../shared/projects";

let root: string;

beforeEach(async () => {
  root = await tempRepo("relay-snapshot-");
  await writeFile(join(root, "IDEAS.md"), "ideas\n");
  gitIn(root, "add", ".");
  gitIn(root, "commit", "-qm", "Initial");
});

afterEach(async () => {
  await rm(root, { recursive: true, force: true });
});

// A script that names its second file well past the trace label's 500 characters.
const script = [
  "python - <<'EOF'",
  "open('IDEAS.md', 'w').write('ideas, trimmed\\n')",
  `notes = """${"research ".repeat(100)}"""`,
  "open('RESEARCH.md', 'w').write(notes)",
  "EOF",
].join("\n");

/** One shell call as each agent reports it: its trace row and its whole command. */
const calls: Record<string, () => { activity: AgentActivity; command: string }> = {
  claude: () => {
    const input = { command: script };
    return {
      activity: claudeActivity("call-1", "Bash", input),
      command: claudeCommand("Bash", input),
    };
  },
  codex: () => {
    const item = { type: "commandExecution", id: "call-1", command: script, status: "inProgress" };
    return { activity: codexActivity("item/started", item)!, command: codexCommand(item) };
  },
  muse: () => {
    const item = {
      itemId: "call-1",
      kind: "toolCall",
      status: "inProgress",
      tool: "run_shell",
      args: JSON.stringify({ command: script }),
    };
    return { activity: museActivity(item)!, command: museCommand(item) };
  },
};

it.each(Object.keys(calls))(
  "credits a %s command with a file it names past the trace label",
  async (provider) => {
    const { activity, command } = calls[provider]!();
    expect(activity.label).not.toContain("RESEARCH.md");
    const snapshot = new TurnSnapshot(root, "answer-1", true);
    await snapshot.start(false);
    await writeFile(join(root, "IDEAS.md"), "ideas, trimmed\n");
    await writeFile(join(root, "RESEARCH.md"), "research\n");
    snapshot.command(activity.id, command);
    snapshot.activity(activity);
    const message = { id: "answer-1" } as ChatMessage;
    await snapshot.finish({ title: "Split notes" } as ProjectChat, message, "claude");
    expect(message.changes?.map((file) => file.path)).toEqual(["IDEAS.md", "RESEARCH.md"]);
  },
);
