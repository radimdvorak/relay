import { z } from "zod";
import { idSchema } from "../../shared/validation";
import { rememberedSecrets } from "../project-chats/remembered-secrets";
import { takes, type ApiContext, type Handlers } from "./context";

const nameSchema = z.string().trim().min(1).max(80);

const ofSchema = z.union([
  z.object({ projectId: idSchema }).strict(),
  z.object({ chatId: idSchema }).strict(),
]);

/** What a project's agents get without asking; names only, never a value. */
export function secretHandlers(ctx: ApiContext) {
  const remembered = rememberedSecrets(ctx.store);
  return {
    projectSecrets: takes([ofSchema], async (of) =>
      remembered.list(
        "projectId" in of
          ? of.projectId
          : (await ctx.projectChats.get(of.chatId)).projectId,
      ),
    ),
    forgetProjectSecret: takes(
      [idSchema, nameSchema],
      async (projectId, name) => {
        await remembered.forget(projectId, name);
        return remembered.list(projectId);
      },
    ),
  } satisfies Handlers;
}
