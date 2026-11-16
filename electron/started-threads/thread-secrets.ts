import { secretFileUse } from "../../shared/secret-request";
import type { ProjectChats } from "../project-chats";
import { toolText, type RelayToolArgs } from "../relay-mcp";

/**
 * Leaves a card asking for the secret in the agent's answer. Like an asked
 * page, the answer comes as the user's next message, so nothing waits on a
 * tool call that a client would time out; that message carries a file's path,
 * never the value. A secret the user had Relay remember for the project comes
 * back at once the same way, as a path.
 */
export async function askForSecret(
  chats: Pick<ProjectChats, "ask">,
  chatId: string,
  secret: RelayToolArgs<"request_secret">,
) {
  const file = await chats.ask(chatId, { secret });
  if (file)
    return toolText(
      [
        `The user chose to always give "${secret.name}" in this project, so there was no card. It is in this private file, not in the chat:`,
        file,
        secretFileUse(file),
      ].join("\n"),
    );
  return toolText(
    `The user sees a card asking for "${secret.name}". Their answer comes as their next message: the path of a private file holding the value, or word that they declined. You never see the value itself. If you can't go on without it, end your turn now and wait.`,
  );
}
