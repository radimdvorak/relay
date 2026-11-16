import { describe, expect, it } from "vitest";
import type { ChatSummary } from "../../shared/projects";
import type { PushMessage } from "../../shared/push";
import type { NewsItem } from "../../shared/thread-news";
import { PhonePush, type PushOutcome, type PushPhone } from "./phone-push";

function chat(patch: Partial<ChatSummary> = {}): ChatSummary {
  return {
    id: "c1",
    projectId: "p1",
    title: "Fix the login form",
    scope: { kind: "project" },
    created: 1,
    updated: 100,
    ...patch,
  } as ChatSummary;
}

function news(patch: Partial<NewsItem> = {}): NewsItem {
  return {
    seq: 1,
    at: Date.now(),
    projectId: "p1",
    chatId: "c1",
    kind: "finished",
    title: "Fix the login form",
    body: "Claude finished",
    ...patch,
  };
}

function setup({ hideTitles = false } = {}) {
  const sent: PushMessage[] = [];
  const forgotten: string[] = [];
  const state = {
    on: true,
    present: false,
    onScreen: new Set<string>(),
    chat: chat(),
    outcome: "sent" as PushOutcome,
  };
  const phones: PushPhone[] = [
    { id: "phone", push: { token: "t1", computer: "mac", hideTitles } },
  ];
  const push = new PhonePush({
    on: () => state.on,
    phones: () => phones,
    onScreen: () => state.onScreen,
    present: () => state.present,
    chat: () => state.chat,
    forget: (id, token) => forgotten.push(`${id} ${token}`),
    post: async (message) => {
      sent.push(message);
      return state.outcome;
    },
  });
  return { push, sent, forgotten, state };
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

describe("PhonePush", () => {
  it("sends the title and how it ended, never the answer", () => {
    const { push, sent } = setup();

    push.news(news());

    expect(sent).toEqual([
      {
        token: "t1",
        computer: "mac",
        chatId: "c1",
        kind: "finished",
        title: "Fix the login form",
      },
    ]);
  });

  it("leaves the title out for a phone that hides titles", () => {
    const { push, sent } = setup({ hideTitles: true });

    push.news(news());

    expect(sent[0]).not.toHaveProperty("title");
  });

  it("skips a phone whose app is open, which shows the news itself", () => {
    const { push, sent, state } = setup();
    state.onScreen.add("phone");

    push.news(news());

    expect(sent).toEqual([]);
  });

  it("skips a finished thread an agent started, which that agent waits for", () => {
    const { push, sent } = setup();

    push.news(news({ byAgent: true }));

    expect(sent).toEqual([]);
  });

  it("holds news while someone is at the computer and sends it once they leave", () => {
    const { push, sent, state } = setup();
    state.present = true;

    push.news(news());
    push.presenceChanged();
    expect(sent).toEqual([]);

    state.present = false;
    push.presenceChanged();
    expect(sent.map((m) => m.chatId)).toEqual(["c1"]);

    push.presenceChanged();
    expect(sent).toHaveLength(1);
  });

  it("drops held news of a thread read or answered again before they left", () => {
    const { push, sent, state } = setup();
    state.present = true;
    push.news(news({ chatId: "c1" }));

    state.chat = chat({ seenAt: 150, updated: 100 });
    state.present = false;
    push.presenceChanged();
    expect(sent).toEqual([]);

    state.present = true;
    state.chat = chat();
    push.news(news());
    state.chat = chat({ updated: Date.now() + 1000 });
    state.present = false;
    push.presenceChanged();
    expect(sent).toEqual([]);
  });

  it("sends a held answer the thread list heard of only after its news", () => {
    const { push, sent, state } = setup();
    state.present = true;
    state.chat = chat({ updated: 50 });
    const finished = news();

    push.news(finished);
    state.chat = chat({ updated: finished.at - 5 });
    state.present = false;
    push.presenceChanged();

    expect(sent.map((m) => m.chatId)).toEqual(["c1"]);
  });

  it("sends nothing while phone access is off, held news included", () => {
    const { push, sent, state } = setup();
    state.present = true;
    push.news(news());

    state.on = false;
    state.present = false;
    push.presenceChanged();
    push.news(news({ chatId: "c2" }));

    expect(sent).toEqual([]);
  });

  it("forgets only the token Firebase no longer knows", async () => {
    const { push, forgotten, state } = setup();
    state.outcome = "unregistered";

    push.news(news());
    await settle();

    expect(forgotten).toEqual(["phone t1"]);
  });
});
