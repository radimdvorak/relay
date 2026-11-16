// Phone notifications through Firebase, for a phone that isn't connected: the
// computer posts a push to Relay's push service (push/worker.ts), which holds
// the Firebase key and hands it on. Only a thread's title and how it ended
// travel, never the answer, and with `hideTitles` not even the title.
import type { ThreadNews } from "./thread-news";

/** Relay's push service; it keeps nothing it's sent. */
export const pushUrl = "https://push.relaycode.io/send";

/** What a phone hands the computer so it can be told while away; from `pushBridge`. */
export interface PushRegistration {
  /** The phone's Firebase token. */
  token: string;
  /** What the phone calls this computer, said back so a tap opens the right one. */
  computer: string;
  /** Pushes say only how a thread ended, not which. */
  hideTitles: boolean;
}

/** One push, as the computer posts it and the push service checks it. */
export interface PushMessage {
  token: string;
  computer: string;
  chatId: string;
  kind: ThreadNews["kind"];
  /** Left out when the phone hides titles. */
  title?: string;
}

export const pushLimits = {
  token: 4096,
  computer: 200,
  chatId: 200,
  title: 200,
} as const;

const kinds: readonly string[] = [
  "waiting",
  "finished",
  "failed",
] satisfies ThreadNews["kind"][];

/** The message the push service sends on, or undefined for anything else. */
export function pushMessageFrom(body: unknown): PushMessage | undefined {
  if (typeof body !== "object" || body === null) return;
  const { token, computer, chatId, kind, title } = body as Record<
    string,
    unknown
  >;
  const fits = (value: unknown, max: number) =>
    typeof value === "string" && value.length > 0 && value.length <= max;
  if (!fits(token, pushLimits.token)) return;
  if (!fits(computer, pushLimits.computer)) return;
  if (!fits(chatId, pushLimits.chatId)) return;
  if (typeof kind !== "string" || !kinds.includes(kind)) return;
  if (title !== undefined && !fits(title, pushLimits.title)) return;
  return {
    token: token as string,
    computer: computer as string,
    chatId: chatId as string,
    kind: kind as PushMessage["kind"],
    ...(title !== undefined ? { title: title as string } : {}),
  };
}

/**
 * The Firebase request for a push: data only, so the phone words and shows
 * the notification itself, at high priority so it wakes a dozing phone.
 */
export function firebaseRequest(message: PushMessage) {
  const { token, ...data } = message;
  return {
    message: {
      token,
      data,
      android: { priority: "high", ttl: "86400s" },
    },
  };
}

/** A title cut to what a push carries, without half an emoji. */
export function pushTitle(title: string) {
  if (title.length <= pushLimits.title) return title;
  const cut = title
    .slice(0, pushLimits.title - 1)
    .replace(/[\uD800-\uDBFF]$/, "");
  return `${cut}…`;
}
