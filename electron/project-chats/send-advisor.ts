import type { ThreadAdvisor } from "../../shared/advisor";
import type { ProjectChat, ProjectChatSend } from "../../shared/projects";
import { recipient, sentAgent } from "../../shared/recipient";

/**
 * `input` with the advisor its agent ran with last in `thread`, when it
 * names none: phones, schedules and other threads send without one.
 */
export function withThreadAdvisor(
  input: ProjectChatSend,
  thread: Pick<ProjectChat, "messages" | "lastInput">,
): ProjectChatSend {
  if (input.advisor || recipient(input) === "message") return input;
  const advisor = lastAdvisorOf(thread, sentAgent(input));
  return advisor ? { ...input, advisor } : input;
}

/** What `agent` was last asked with in `thread`; notes to people don't count. */
function lastAdvisorOf(
  { lastInput, messages }: Pick<ProjectChat, "messages" | "lastInput">,
  agent: string,
): ThreadAdvisor | undefined {
  // A review lead's first turn has no message of its own, only lastInput.
  const lastAskedAgent = lastInput && recipient(lastInput) !== "message";
  if (lastAskedAgent && sentAgent(lastInput) === agent)
    return lastInput.advisor;
  for (let i = messages.length - 1; i >= 0; i--) {
    const settings = messages[i].sentSettings;
    if (messages[i].role === "user" && settings?.provider === agent)
      return settings.advisor;
  }
  return undefined;
}
