import {
  existsSync,
  mkdtempSync,
  readdirSync,
  readFileSync,
  statSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it, vi } from "vitest";
import { defaultAISettings } from "../../shared/settings";
import {
  PAGE_QUESTION_ID,
  type AsyncAgentQuestions,
  type ChatMessage,
  type ProjectChat,
  type ProjectChatSend,
} from "../../shared/projects";
import {
  AsyncQuestions,
  answerOf,
  supersedeQuestions,
} from "./async-questions";
import { ActiveTurns } from "./active";
import { rememberedSecrets } from "./remembered-secrets";
import { threadControl } from "./control";
import type { ChatCore } from "./core";

// The OS credential store, standing in: sealed text is the value reversed.
vi.mock("../platform/seal", () => {
  const reversed = async (text: string) => [...text].reverse().join("");
  return { seal: reversed, unseal: reversed, canSeal: async () => true };
});

function fixture() {
  const message: ChatMessage = {
    id: "message",
    role: "assistant",
    body: "",
    provider: "codex",
    status: "streaming",
    created: 1,
    version: 1,
    questions: [
      {
        id: "item",
        questions: [
          { id: "0", question: "Which?" },
          { id: "1", question: "Who?" },
        ],
      },
    ],
  };
  const input: ProjectChatSend = {
    id: "user",
    body: "Work",
    provider: "codex",
    to: "codex",
    choice: defaultAISettings.questions,
    runtimeMode: "approval-required",
    interactionMode: "default",
  };
  const chat = {
    id: "chat",
    projectId: "project",
    messages: [message],
    lastInput: input,
  } as ProjectChat;
  const dir = mkdtempSync(join(tmpdir(), "relay-async-questions-"));
  /** What the store would write to state.json. */
  const state: Record<string, unknown> = {};
  const active = new ActiveTurns(() => {});
  const run = active.claim(chat.id, input);
  run.steer = vi.fn().mockResolvedValue(undefined);
  const save = vi.fn().mockResolvedValue(undefined);
  const emit = vi.fn();
  const core = {
    storage: { load: async () => chat, save },
    store: {
      aiSettings: () => defaultAISettings,
      dir,
      get: () => state,
      update: async (change: (s: typeof state) => void) => change(state),
    },
    active,
    control: threadControl(),
    closing: () => false,
    emit,
  } as unknown as ChatCore;
  const send = vi.fn().mockResolvedValue(undefined);
  return {
    questions: new AsyncQuestions(core, send),
    secretsDir: join(dir, "secrets"),
    state,
    remembered: rememberedSecrets(core.store),
    active,
    chat,
    message,
    run,
    save,
    emit,
    send,
  };
}

const answer = {
  kind: "question" as const,
  answers: { "0": ["Private"], "1": ["Owner"] },
};

it("dismisses and reopens a saved question without answering, steering or stopping the turn", async () => {
  const f = fixture();
  const version = f.message.version;
  await f.questions.setDismissed("chat", "message", "item", true);
  expect(f.message.questions![0]).toMatchObject({ dismissed: true });
  expect(f.message.questions![0].answers).toBeUndefined();
  expect(f.message.version).toBeGreaterThan(version);
  expect(f.save).toHaveBeenCalledWith(f.chat);
  expect(f.emit).toHaveBeenCalledWith({
    chatId: "chat",
    message: f.message,
  });
  expect(f.message.status).toBe("streaming");
  expect(f.active.stopping(f.run)).toBe(false);
  expect(f.run.steer).not.toHaveBeenCalled();
  expect(f.send).not.toHaveBeenCalled();
  expect(f.chat.messages).toHaveLength(1);

  await f.questions.setDismissed("chat", "message", "item", false);
  expect(f.message.questions![0].dismissed).toBeUndefined();
  await f.questions.answer("chat", "message", "item", answer);
  expect(f.message.questions![0].answers).toEqual(answer.answers);
});

it("serializes dismissal with an answer and rejects dismissing an answered or missing question", async () => {
  const f = fixture();
  const results = await Promise.allSettled([
    f.questions.setDismissed("chat", "message", "item", true),
    f.questions.answer("chat", "message", "item", answer),
    f.questions.setDismissed("chat", "message", "item", true),
  ]);
  expect(results.map((r) => r.status)).toEqual([
    "fulfilled",
    "fulfilled",
    "rejected",
  ]);
  expect(f.message.questions![0].answers).toEqual(answer.answers);
  expect(f.message.questions![0].dismissed).toBeUndefined();
  await expect(
    f.questions.setDismissed("chat", "missing", "item", true),
  ).rejects.toThrow("no longer available");
  await expect(
    f.questions.setDismissed("chat", "message", "missing", true),
  ).rejects.toThrow("no longer available");
});

it("keeps dismissal retryable if saving fails", async () => {
  const f = fixture();
  f.save.mockRejectedValueOnce(new Error("Disk full"));
  await expect(
    f.questions.setDismissed("chat", "message", "item", true),
  ).rejects.toThrow("Disk full");
  expect(f.message.questions![0].dismissed).toBeUndefined();
  expect(f.emit).not.toHaveBeenCalled();
  await f.questions.setDismissed("chat", "message", "item", true);
  f.save.mockRejectedValueOnce(new Error("Disk full"));
  await expect(
    f.questions.setDismissed("chat", "message", "item", false),
  ).rejects.toThrow("Disk full");
  expect(f.message.questions![0].dismissed).toBe(true);
});

it("rejects missing, partial, unknown and malformed answers without sending or consuming the question", async () => {
  const f = fixture();
  for (const value of [
    { kind: "question", answers: { "0": ["Private"] } },
    { kind: "question", answers: { ...answer.answers, extra: ["x"] } },
    { kind: "question", answers: { ...answer.answers, "1": [" "] } },
    { kind: "approval", decision: "accept" },
    { kind: "question", answers: { "0": [4], "1": ["Owner"] } },
  ]) {
    await expect(
      f.questions.answer("chat", "message", "item", value as never),
    ).rejects.toThrow();
  }
  await expect(
    f.questions.answer("chat", "missing", "item", answer),
  ).rejects.toThrow("no longer available");
  await expect(
    f.questions.answer("chat", "message", "missing", answer),
  ).rejects.toThrow("no longer available");
  expect(f.run.steer).not.toHaveBeenCalled();
  expect(f.send).not.toHaveBeenCalled();
  expect(f.message.questions![0].answers).toBeUndefined();
});

it("rolls back a refused steer and leaves the question answerable on retry", async () => {
  const f = fixture();
  vi.mocked(f.run.steer!).mockRejectedValueOnce(new Error("Turn ended"));
  await expect(
    f.questions.answer("chat", "message", "item", answer),
  ).rejects.toThrow("Turn ended");
  expect(f.chat.messages).toEqual([f.message]);
  expect(f.message.questions![0].answers).toBeUndefined();
  expect(f.message.questions![0].answerMessageId).toBeUndefined();
  await f.questions.answer("chat", "message", "item", answer);
  expect(f.chat.messages).toHaveLength(2);
  expect(f.chat.messages[1]).toMatchObject({
    asyncQuestionAnswer: true,
    questionMessageId: "message",
    unread: true,
    status: "complete",
  });
  expect(f.message.questions![0].answers).toEqual(answer.answers);
  expect(f.message.questions![0].answerMessageId).toBe(f.chat.messages[1].id);
});

it("serializes simultaneous replies so the question is answered once", async () => {
  const f = fixture();
  const results = await Promise.allSettled([
    f.questions.answer("chat", "message", "item", answer),
    f.questions.answer("chat", "message", "item", answer),
  ]);
  expect(results.map((r) => r.status)).toEqual(["fulfilled", "rejected"]);
  expect(f.run.steer).toHaveBeenCalledTimes(1);
});

it("doesn't inject an answer into another agent or conversation, and preserves their settings", async () => {
  for (const input of [
    { provider: "claude" as const, to: "claude" as const },
    { parentId: "other-thread" },
  ]) {
    const f = fixture();
    f.run.input = { ...f.run.input!, ...input };
    await f.questions.answer("chat", "message", "item", answer);
    expect(f.run.steer).not.toHaveBeenCalled();
    expect(f.send).toHaveBeenCalledWith(
      "chat",
      expect.objectContaining({
        provider: "codex",
        to: "codex",
        runtimeMode: "approval-required",
        asyncQuestionAnswer: true,
        questionMessageId: "message",
      }),
    );
    expect(f.send.mock.calls[0][1].delivery).toBeUndefined();
    expect(f.message.questions![0].answerMessageId).toBe(
      f.send.mock.calls[0][1].id,
    );
  }
});

it("leaves a failed follow-up answerable and never silently loses the reply", async () => {
  const f = fixture();
  f.run.steer = undefined;
  f.send.mockRejectedValueOnce(new Error("Queue full"));
  await expect(
    f.questions.answer("chat", "message", "item", answer),
  ).rejects.toThrow("Queue full");
  expect(f.message.questions![0].answers).toBeUndefined();
});

it("folds away the agent's open questions in the conversation a new turn is typed into", () => {
  const asked = (
    id: string,
    provider: "codex" | "claude",
    parentId?: string,
    answers?: Record<string, string[]>,
  ): ChatMessage => ({
    id,
    role: "assistant",
    body: "",
    provider,
    status: "complete",
    created: 1,
    version: 1,
    ...(parentId ? { parentId } : {}),
    questions: [
      {
        id: id + "-q",
        questions: [{ id: "0", question: "?" }],
        ...(answers && { answers }),
      },
    ],
  });
  const stale = asked("stale", "codex");
  const answered = asked("answered", "codex", undefined, { "0": ["Yes"] });
  const otherAgent = asked("other-agent", "claude");
  const aside = asked("aside", "codex", "root");
  const changed = supersedeQuestions(
    [stale, answered, otherAgent, aside],
    "codex",
    undefined,
  );
  expect(changed).toEqual([stale]);
  expect(stale).toMatchObject({ version: 2, questions: [{ dismissed: true }] });
  expect(answered.questions![0].dismissed).toBeUndefined();
  expect(otherAgent.questions![0].dismissed).toBeUndefined();
  expect(aside.questions![0].dismissed).toBeUndefined();
});

describe("answerOf, for a page the agent asked with", () => {
  const group: AsyncAgentQuestions = {
    id: "g",
    page: { id: "g", title: "Pick a sound", pages: [{}], created: 1 },
    questions: [{ id: PAGE_QUESTION_ID, question: "Pick a sound" }],
  };

  it("hands the agent the page's JSON, or word of a skip", () => {
    expect(
      answerOf(group, { kind: "page", answer: '{"sound":"glass"}' }),
    ).toEqual({
      body: 'Answer to your page "Pick a sound":\n{"sound":"glass"}',
      answers: { [PAGE_QUESTION_ID]: ['{"sound":"glass"}'] },
      pageAnswer: { title: "Pick a sound" },
    });
    expect(answerOf(group, { kind: "page", answer: null })).toEqual({
      body: 'I skipped your page "Pick a sound". Decide on your own, and say what you picked.',
      answers: {},
      pageAnswer: { title: "Pick a sound", skipped: true },
    });
  });

  it("takes the words of a phone too old to show the page", () => {
    expect(
      answerOf(group, {
        kind: "question",
        answers: { [PAGE_QUESTION_ID]: ["  the glass one "] },
      }).body,
    ).toBe('Answer to your page "Pick a sound":\nthe glass one');
  });

  it("refuses an approval for it", () => {
    expect(() =>
      answerOf(group, { kind: "approval", decision: "accept" }),
    ).toThrow("Invalid response type");
  });
});

describe("a secret request", () => {
  const VALUE = "ghp_s3cr3t-value";

  /** A thread whose agent asked for a token and went on working. */
  function asked() {
    const f = fixture();
    f.message.questions = [
      {
        id: "item",
        secret: { name: "GitHub token", reason: "To publish the release." },
        questions: [
          { id: "secret", question: "To publish the release.", isSecret: true },
        ],
      },
    ];
    const savedFiles = () =>
      existsSync(f.secretsDir)
        ? readdirSync(f.secretsDir).map((name) => join(f.secretsDir, name))
        : [];
    /** Everything Relay keeps or says about the thread, as text. */
    const kept = () =>
      JSON.stringify([
        f.chat,
        f.emit.mock.calls,
        vi.mocked(f.run.steer)?.mock.calls,
      ]);
    return { ...f, savedFiles, kept };
  }

  it("puts the value in a file only this user reads, and tells the agent the path", async () => {
    const f = asked();
    await f.questions.answer("chat", "message", "item", {
      kind: "secret",
      value: `  ${VALUE} `,
    });
    const [file] = f.savedFiles();
    expect(readFileSync(file, "utf8")).toBe(VALUE);
    if (process.platform !== "win32")
      expect(statSync(file).mode & 0o777).toBe(0o600);
    expect(f.run.steer).toHaveBeenCalledWith(
      expect.stringContaining(file),
      expect.any(String),
    );
    expect(f.message.questions![0].answers).toEqual({ secret: ["Saved"] });
    expect(f.kept()).not.toContain(VALUE);
  });

  it("takes the value from a phone that answered the hidden question", async () => {
    const f = asked();
    await f.questions.answer("chat", "message", "item", {
      kind: "question",
      answers: { secret: [VALUE] },
    });
    expect(readFileSync(f.savedFiles()[0], "utf8")).toBe(VALUE);
    expect(f.message.questions![0].answers).toEqual({ secret: ["Saved"] });
    expect(f.kept()).not.toContain(VALUE);
  });

  it("remembers it for the project when the user answers Always", async () => {
    const f = asked();
    await f.questions.answer("chat", "message", "item", {
      kind: "secret",
      value: VALUE,
      remember: true,
    });
    expect(await f.remembered.value("project", "github TOKEN")).toBe(VALUE);
    expect(await f.remembered.value("project", "GitHub")).toBeUndefined();
    expect(f.run.steer).toHaveBeenCalledWith(
      expect.stringContaining("always give it in this project"),
      expect.any(String),
    );
    expect(f.message.questions![0].answers).toEqual({ secret: ["Always"] });
    // Sealed in what the store writes, and nowhere in the thread.
    expect(JSON.stringify(f.state)).not.toContain(VALUE);
    expect(f.kept()).not.toContain(VALUE);
  });

  it("remembers nothing for a plain Save or a decline", async () => {
    const f = asked();
    await f.questions.answer("chat", "message", "item", {
      kind: "secret",
      value: VALUE,
    });
    expect(f.remembered.list("project")).toEqual([]);
  });

  it("tells the agent not to ask in chat when the user declines", async () => {
    const f = asked();
    await f.questions.answer("chat", "message", "item", {
      kind: "secret",
      value: null,
    });
    expect(f.run.steer).toHaveBeenCalledWith(
      expect.stringMatching(/declined.*Don't ask for it in chat/),
      expect.any(String),
    );
    expect(f.message.questions![0].answers).toEqual({});
    expect(f.savedFiles()).toEqual([]);
  });

  it("removes the file when the agent never heard where it is", async () => {
    const f = asked();
    f.run.steer = vi.fn().mockRejectedValue(new Error("The turn ended."));
    await expect(
      f.questions.answer("chat", "message", "item", {
        kind: "secret",
        value: VALUE,
      }),
    ).rejects.toThrow("The turn ended.");
    expect(f.savedFiles()).toEqual([]);
    expect(f.message.questions![0].answers).toBeUndefined();
    expect(f.kept()).not.toContain(VALUE);
  });

  it("refuses an answer that isn't a secret's", async () => {
    const f = asked();
    await expect(
      f.questions.answer("chat", "message", "item", {
        kind: "page",
        answer: VALUE,
      }),
    ).rejects.toThrow("Invalid response type.");
    await expect(
      f.questions.answer("chat", "message", "item", {
        kind: "secret",
        value: "   ",
      }),
    ).rejects.toThrow("Type the secret");
  });
});
