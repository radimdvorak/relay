// What a phone may pass to the calls phone-remote answers itself, rather
// than the bridge's (./call-args).
import { z } from "zod";
import { maxDictationChunk, maxReadAloudText } from "../../shared/remote";

const version = z.string().regex(/^\d+\.\d+\.\d+(?:\.\d+)?$/);
export const appReportSchema = z
  .object({
    version,
    updated: z.boolean(),
    apk: version,
    updates: z.boolean(),
    update: z
      .object({
        kind: z.enum(["downloading", "ready", "apk"]),
        version,
      })
      .strict()
      .optional(),
    failed: version.optional(),
  })
  .strict();

const dictationId = z.number().int().nonnegative();

export const dictationSchema = z.discriminatedUnion("type", [
  z.object({ type: z.literal("start"), id: dictationId }).strict(),
  z
    .object({
      type: z.literal("audio"),
      id: dictationId,
      pcm: z
        .string()
        .max(maxDictationChunk)
        .regex(/^[A-Za-z0-9+/]*={0,2}$/),
    })
    .strict(),
  z.object({ type: z.literal("stop"), id: dictationId }).strict(),
  z.object({ type: z.literal("cancel"), id: dictationId }).strict(),
]);

const readingId = z.number().int().nonnegative();

export const readAloudSchema = z.discriminatedUnion("type", [
  z
    .object({
      type: z.literal("start"),
      id: readingId,
      markdown: z.string().max(maxReadAloudText),
    })
    .strict(),
  z.object({ type: z.literal("pull"), id: readingId }).strict(),
  z.object({ type: z.literal("stop"), id: readingId }).strict(),
]);

export const portSchema = z.number().int().min(1).max(65535);
