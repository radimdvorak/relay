import { z } from "zod";
import { idSchema } from "../../shared/validation";
import { rememberedSecrets } from "../project-chats/remembered-secrets";
import { takes, type ApiContext, type Handlers } from "./context";

const nameSchema = z.string().trim().min(1).max(80);

/**
 * What a project's agents get without asking; names only, never a value.
 * For a thread or a project on another computer, that computer answers.
 */
export function secretHandlers(ctx: ApiContext) {
  const remembered = rememberedSecrets(ctx.store);
  return {
    threadSecrets: takes([idSchema], async (chatId) =>
      remembered.saved((await ctx.projectChats.get(chatId)).projectId),
    ),
    projectSecrets: takes([idSchema], (projectId) =>
      remembered.saved(projectId),
    ),
    forgetProjectSecret: takes([idSchema, nameSchema], (projectId, name) =>
      remembered.forget(projectId, name),
    ),
  } satisfies Handlers;
}
