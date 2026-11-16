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
  /** Phone access is on; while it's off nothing goes out, held news included. */
  on(): boolean;
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

/** `busy` may pass: the push service or Firebase couldn't take it just now. */
export type PushOutcome = "sent" | "unregistered" | "busy" | "failed";

interface Held {
  item: NewsItem;
  /** The thread's snooze when the news came; one set since makes it old news. */
  snoozedUntil?: number;
}

/** A phone whose link dies on screen is told of what it skipped this long before. */
const screenLostWithin = 60_000;
/** Waits before trying a busy push again; the news is gone past these. */
const retryDelays = [10_000, 60_000];

/**
 * Tells phones that aren't looking of a thread that finished, failed or
 * needs you, through Relay's push service. While someone sits at the
 * computer the news waits, and goes out when they leave if it's news still,
 * as a connected phone's own notifications do (HeldNews.kt).
 */
export class PhonePush {
  private held = new Map<string, Held>();
  /** News each phone skipped as its app was on screen, should that turn out to be a dead link. */
  private skipped = new Map<string, Held[]>();

  constructor(private host: PushHost) {}

  news(item: NewsItem) {
    if (!this.host.on()) return;
    // The agent that started the thread is the one waiting for it.
    if (item.kind === "finished" && item.byAgent) return;
    if (!this.host.phones().length) return;
    const held = this.heldNews(item);
    if (!this.host.present()) return this.send(held);
    this.held.set(item.chatId, held);
  }

  /** Someone may have left the computer: what's still news goes out. */
  presenceChanged() {
    if (!this.held.size || this.host.present()) return;
    const due = [...this.held.values()].filter((held) => this.stillNews(held));
    this.held.clear();
    for (const held of due) this.send(held);
  }

  /**
   * A phone's link went quiet with its app on screen, so the news it skipped
   * as shown there may never have reached it.
   */
  screenLost(phoneId: string) {
    const skipped = this.skipped.get(phoneId) ?? [];
    this.skipped.delete(phoneId);
    // Back on another link already.
    if (this.host.onScreen().has(phoneId)) return;
    const phone = this.host.phones().find((p) => p.id === phoneId);
    if (!phone) return;
    const since = Date.now() - screenLostWithin;
    for (const held of skipped)
      if (held.item.at >= since && this.stillNews(held))
        void this.deliver(phone, held.item);
  }

  private heldNews(item: NewsItem): Held {
    const thread = this.host.chat(item.projectId, item.chatId);
    return thread?.snoozedUntil === undefined
      ? { item }
      : { item, snoozedUntil: thread.snoozedUntil };
  }

  private send(held: Held) {
    const onScreen = this.host.onScreen();
    for (const phone of this.host.phones()) {
      if (onScreen.has(phone.id)) this.skip(phone.id, held);
      else void this.deliver(phone, held.item);
    }
  }

  private skip(phoneId: string, held: Held) {
    const since = Date.now() - screenLostWithin;
    const recent = (this.skipped.get(phoneId) ?? []).filter(
      (h) => h.item.at >= since,
    );
    this.skipped.set(phoneId, [...recent, held]);
  }

  /** Posts the push, and tries a busy one again a couple of times while the phone still wants it. */
  private async deliver(phone: PushPhone, item: NewsItem) {
    for (let attempt = 0; ; attempt++) {
      if (!this.host.on()) return;
      const outcome = await (this.host.post ?? postPush)(message(phone, item));
      if (outcome === "unregistered")
        return this.host.forget(phone.id, phone.push.token);
      if (outcome !== "busy" || attempt >= retryDelays.length) return;
      await wait(retryDelays[attempt]);
      const again = this.host
        .phones()
        .find((p) => p.id === phone.id && p.push.token === phone.push.token);
      // It forgot this token, or opened the app meanwhile.
      if (!again || this.host.onScreen().has(phone.id)) return;
      phone = again;
    }
  }

  /**
   * Unread, and neither snoozed, settled nor answered since. The list hears
   * of a finished answer only after its news, so `updated` past the news is
   * a turn since, which makes the held answer an old one.
   */
  private stillNews({ item, snoozedUntil }: Held, at = Date.now()) {
    const now = this.host.chat(item.projectId, item.chatId);
    if (!now) return false;
    const read = !!now.seenAt && now.seenAt >= now.updated;
    if (read) return false;
    const snoozedSince =
      (now.snoozedUntil ?? 0) > at && now.snoozedUntil !== snoozedUntil;
    if (snoozedSince) return false;
    if (item.kind === "waiting") return !!now.waiting;
    const answeredSince = now.updated > item.at;
    if (item.kind === "failed") return !answeredSince;
    return !answeredSince && !chatSettled(now);
  }
}

function message({ push }: PushPhone, item: NewsItem): PushMessage {
  return {
    token: push.token,
    computer: push.computer,
    chatId: item.chatId,
    kind: item.kind,
    ...(push.hideTitles ? {} : { title: pushTitle(item.title) }),
  };
}

function wait(ms: number) {
  // Doesn't keep a quitting Relay up.
  return new Promise<void>((resolve) => setTimeout(resolve, ms).unref?.());
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
    return mayPass(response.status) ? "busy" : "failed";
  } catch (e) {
    // Not the message: it holds the thread's title.
    console.warn("Phone push: couldn't reach the push service:", String(e));
    return "busy";
  }
}

/** Too many at once, or the service or Firebase having a moment; 502 is Firebase refusing it. */
function mayPass(status: number) {
  return status === 429 || (status >= 500 && status !== 502);
}
