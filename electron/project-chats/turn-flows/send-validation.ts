import { agentInfo, agentName, isRegistryProvider } from "../../../shared/agents";
import type { ChatMessage, ProjectChatSend } from "../../../shared/projects";
import type { agentAsked } from "../../../shared/recipient";
import { parseGoalCommand } from "../../../shared/goal";

/** Checks a send's mention and `/goal` rules before anything is saved. */
export function validateSend(
  asked: ReturnType<typeof agentAsked>,
  input: ProjectChatSend,
  parent: ChatMessage | undefined,
) {
  if (asked && !asked.question && !input.images?.length && !input.audio?.length)
    throw new Error("Add a question after the agent mention.");
  if (
    asked &&
    input.audio?.length &&
    !isRegistryProvider(asked.provider) &&
    !agentInfo(asked.provider).audio
  )
    throw new Error(`${agentName(asked.provider)} can't listen to audio.`);
  const goal = asked && parseGoalCommand(asked.question);
  if (!goal) return;
  if (asked.provider !== "codex" && asked.provider !== "claude")
    throw new Error("Only Codex and Claude Code keep working toward a goal.");
  // Claude would take the word for a new objective.
  if (
    asked.provider === "claude" &&
    (goal.type === "pause" || goal.type === "resume")
  )
    throw new Error(
      "Claude Code can't pause or resume a goal. /goal clear ends it.",
    );
  if (parent)
    throw new Error("Set a goal in the thread's main conversation.");
}
