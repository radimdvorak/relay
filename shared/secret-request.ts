// What an agent asks for with request_secret: something only the user has (an
// API key, a token, a password). The user answers in a card; the value goes
// to a private file on the computer the agent runs on, and the thread keeps
// only that it was saved or declined.

import { z } from "zod";

/** What the agent calls the secret and why it needs it; both show on the card. */
export const secretRequestSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1)
      .max(80)
      .describe(
        "What the secret is, in a few words, like “npm publish token”; it heads the card.",
      ),
    reason: z
      .string()
      .trim()
      .min(1)
      .max(400)
      .optional()
      .describe("What you need it for and where the user finds it."),
  })
  .strict();

export type SecretRequest = z.infer<typeof secretRequestSchema>;

/** The one hidden question a secret request carries, for apps that show it as a question. */
export const SECRET_QUESTION_ID = "secret";

/** What the thread keeps under SECRET_QUESTION_ID once the user saved the secret, in place of its value. */
export const SECRET_SAVED = "Saved";

/** Longer than any key or certificate a card's one line should take. */
export const SECRET_MAX_CHARS = 8000;

/** How long the file holding a saved secret stays before Relay deletes it. */
export const SECRET_FILE_LIFE_HOURS = 24;
