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

/** What the thread keeps instead of SECRET_SAVED when the user answered Always. */
export const SECRET_ALWAYS = "Always";

/** What the thread keeps for a request Relay answered itself, from what the user had it remember. */
export const SECRET_REMEMBERED = "Remembered";

/**
 * What two names must share to be the same remembered secret: their letters,
 * whatever the case and spacing. Nothing looser, so a lookalike name never
 * gets another secret's value.
 */
export const secretNameKey = (name: string) =>
  name.trim().replace(/\s+/g, " ").toLocaleLowerCase();

/** A secret Relay remembers for a project; its value never leaves the main process. */
export interface RememberedSecret {
  name: string;
  saved: number;
  /** Kept only until Relay quits: this computer has no credential store to seal it with. */
  sessionOnly?: true;
}

/** A project, or the one a thread is in. */
export type SecretsOf = { projectId: string } | { chatId: string };

/** What a project has saved, as a card or its settings shows it. */
export interface SavedSecrets {
  secrets: RememberedSecret[];
  /** This computer has no credential store, so Always lasts only until Relay quits. */
  sessionOnly?: true;
}

export interface ProjectSecretsApi {
  /** The secrets the user answered with Always in the project, by name. */
  projectSecrets(of: SecretsOf): Promise<SavedSecrets>;
  /** Stops remembering one. */
  forgetProjectSecret(projectId: string, name: string): Promise<void>;
}

/**
 * What the card says Always does, on the desktop and both phones: which saved
 * secret it makes this one, the names the project already has saved, so a
 * lookalike asked under another name is plain to see, and how long it lasts
 * where the computer can't seal it.
 */
export function alwaysExplained(
  name: string,
  saved: Pick<SavedSecrets, "sessionOnly"> & { secrets: { name: string }[] },
) {
  // The asked name itself can be saved by now, from another thread's card.
  const asked = secretNameKey(name);
  const others = saved.secrets
    .map((secret) => secret.name)
    .filter((other) => secretNameKey(other) !== asked);
  return [
    `Always also keeps it for this project: any agent here that asks for “${name}” by exactly that name gets it without a card.`,
    ...(saved.sessionOnly
      ? [
          "This computer has no credential store, so it is kept only until Relay quits.",
        ]
      : []),
    ...(others.length
      ? [`Already saved here under other names: ${others.join(", ")}.`]
      : []),
  ].join(" ");
}

/** How the agent is told to use the file a secret was put in. */
export const secretFileUse = (file: string) =>
  `Read it only inside the command that uses it, with the path quoted, like TOKEN="$(cat '${file}')" <command>. Never print it, copy it elsewhere or repeat it in your answer. Relay deletes the file in ${SECRET_FILE_LIFE_HOURS} hours.`;

/** Where the card says the value goes. */
export const secretDestination = (agent: string) =>
  `It goes to a private file that ${agent}'s commands read, not into the chat. Relay deletes the file after ${SECRET_FILE_LIFE_HOURS} hours.`;
