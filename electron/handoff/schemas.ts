// What a paired computer sends this one, checked before anything reads it.
import { z } from "zod";
import { handoffChunk, handoffMessagesSchema } from "../../shared/handoff";
import { chatScopeSchema, projectChatSendSchema } from "../../shared/projects";
import { branchNameProblem } from "../../shared/branch-names";

export const partSchema = z.enum(["thread", "bundle"]);
export const base64 = z
  .string()
  .max(Math.ceil(handoffChunk / 3) * 4)
  .regex(/^[A-Za-z0-9+/]*={0,2}$/);
export const shaSchema = z.string().regex(/^[0-9a-f]{40,64}$/);
// Any name git takes: a thread's branch may be one the user chose.
export const branchSchema = z
  .string()
  .max(250)
  .refine((b) => !branchNameProblem(b));
export const repositoriesSchema = z.array(z.string().max(300)).min(1).max(20);

export const settingsSchema = projectChatSendSchema.pick({
  provider: true,
  choice: true,
  runtimeMode: true,
  interactionMode: true,
  contextWindow: true,
});

export const setupRequestSchema = z
  .object({
    url: z.string().trim().min(1).max(2000),
    repositories: repositoriesSchema,
    error: z.string().max(8000),
    settings: settingsSchema,
  })
  .strict();
export const threadSchema = z
  .object({
    from: z.string().trim().min(1).max(80),
    repositories: repositoriesSchema,
    title: z.string().max(200),
    scope: chatScopeSchema,
    settings: settingsSchema,
    messages: handoffMessagesSchema,
    git: z
      .object({
        branch: branchSchema,
        tip: shaSchema,
        bundle: z.boolean(),
        from: branchSchema.optional(),
        start: shaSchema.optional(),
      })
      .strict(),
    start: z.literal(true).optional(),
  })
  .strict();
export const homeThreadSchema = threadSchema
  .pick({
    from: true,
    title: true,
    scope: true,
    settings: true,
    messages: true,
  })
  .strict();
export const folderSchema = z.string().trim().min(1).max(4096);
