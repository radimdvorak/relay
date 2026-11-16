import { chatSettled } from "../../shared/chat-activity";
import type { ChatSummary } from "../../shared/projects";
import {
  pushTitle,
  pushUrl,
  type PushMessage,
  type PushRegistration,
} from "../../shared/push";
import type { NewsItem } from "../../shared/thread-news";

/** A phone that registered for pushes. */
export interface PushPhone {
  id: string;
  push: PushRegistration;
}

export interface PushHost {
  phones(): PushPhone[];
  /** Phones connected with the app on screen, which show news themselves. */
  onScreen(): ReadonlySet<string>;
  /** Someone at this computer working in Relay; see ./presence. */
  present(): boolean;
  chat(projectId: string, chatId: string): ChatSummary | undefined;
  /**
   * Firebase refused `token` for good: the app was uninstalled or its token
   * rotated. A newer one the phone handed over since stays.
   */
  forget(phoneId: string, token: string): void;
  post?(message: PushMessage): Promise<PushOutcome>;
}

export type PushOutcome = "sent" | "unregistered" | "failed";

interface Held {
  item: NewsItem;
  /** The thread as it was when the news came. */
  then: ChatSummary;
}

/**
 * Tells phones that aren't looking of a thread that finished, failed or
 * needs you, through Relay's push service. While someone sits at the
 * computer the news waits, and goes out when they leave if it's news still,
 * as a connected phone's own notifications do (HeldNews.kt).
 */
export class PhonePush {
  private held = new Map<string, Held>();

  constructor(private host: PushHost) {}

  news(item: NewsItem) {
    // The agent that started the thread is the one waiting for it.
    if (item.kind === "finished" && item.byAgent) return;
    if (!this.host.phones().length) return;
    if (!this.host.present()) return this.send(item);
    const then = this.host.chat(item.projectId, item.chatId);
    if (then) this.held.set(item.chatId, { item, then });
  }

  /** Someone may have left the computer: what's still news goes out. */
  presenceChanged() {
    if (!this.held.size || this.host.present()) return;
    const due = [...this.held.values()].filter(({ item, then }) =>
      stillNews(item, then, this.host.chat(item.projectId, item.chatId)),
    );
    this.held.clear();
    for (const { item } of due) this.send(item);
  }

  private send(item: NewsItem) {
    const onScreen = this.host.onScreen();
    for (const phone of this.host.phones()) {
      if (onScreen.has(phone.id)) continue;
      const { token, computer, hideTitles } = phone.push;
      const message: PushMessage = {
        token,
        computer,
        chatId: item.chatId,
        kind: item.kind,
        ...(hideTitles ? {} : { title: pushTitle(item.title) }),
      };
      void (this.host.post ?? postPush)(message).then((outcome) => {
        if (outcome === "unregistered") this.host.forget(phone.id, token);
      });
    }
  }
}

/**
 * Unread, and neither snoozed, settled nor answered since: a turn since makes
 * the held answer an old one.
 */
function stillNews(
  item: NewsItem,
  then: ChatSummary,
  now: ChatSummary | undefined,
  at = Date.now(),
) {
  if (!now) return false;
  const read = !!now.seenAt && now.seenAt >= now.updated;
  if (read) return false;
  const snoozedSince =
    (now.snoozedUntil ?? 0) > at && now.snoozedUntil !== then.snoozedUntil;
  if (snoozedSince) return false;
  if (item.kind === "waiting") return !!now.waiting;
  const answeredSince = now.updated !== then.updated;
  if (item.kind === "failed") return !answeredSince;
  return !answeredSince && !chatSettled(now);
}

async function postPush(message: PushMessage): Promise<PushOutcome> {
  try {
    const response = await fetch(pushUrl, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(message),
      signal: AbortSignal.timeout(15_000),
    });
    if (response.ok) return "sent";
    if (response.status === 410) return "unregistered";
    console.warn(`Phone push: the push service answered ${response.status}.`);
    return "failed";
  } catch (e) {
    // Not the message: it holds the thread's title.
    console.warn("Phone push: couldn't reach the push service:", String(e));
    return "failed";
  }
}
