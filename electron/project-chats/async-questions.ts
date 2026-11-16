import { randomUUID } from "node:crypto";
import {
  agentResponseSchema,
  type AgentResponse,
} from "../../shared/agent-modes";
import type { AgentProvider } from "../../shared/agents";
import {
  PAGE_QUESTION_ID,
  type AsyncAgentQuestions,
  type PageAnswer,
  type ChatMessage,
  type ProjectChat,
  type ProjectChatSend,
} from "../../shared/projects";
import { agentAsked } from "../../shared/recipient";
import {
  SECRET_ALWAYS,
  secretFileUse,
  SECRET_MAX_CHARS,
  SECRET_QUESTION_ID,
  SECRET_SAVED,
  type SecretRequest,
} from "../../shared/secret-request";
import type { ChatCore } from "./core";
import { assertHere } from "./handoff";
import { rememberedSecrets } from "./remembered-secrets";
import { secretFiles, type SecretFiles } from "./secret-files";
import { sessionInput } from "./sessions";

/**
 * A new turn typed to the agent answers its open questions in that
 * conversation, so they stop asking for input. They fold away dismissed,
 * so they can still be reopened and answered. Returns the messages it changed.
 */
export function supersedeQuestions(
  messages: ChatMessage[],
  provider: AgentProvider,
  parentId: string | null | undefined,
) {
  return messages.filter((m) => {
    if (
      m.role !== "assistant" ||
      m.provider !== provider ||
      (m.parentId ?? null) !== (parentId ?? null)
    )
      return false;
    const open = (m.questions ?? []).filter((g) => !g.answers && !g.dismissed);
    for (const group of open) group.dismissed = true;
    if (open.length) m.version++;
    return open.length > 0;
  });
}

/**
 * What answering a group says to the agent, and what the group keeps. A page
 * (ask_html) takes the JSON it handed back, nothing for a skip, or the words
 * of a phone too old to show it.
 */
export function answerOf(
  group: AsyncAgentQuestions,
  response: AgentResponse,
): {
  body: string;
  answers: Record<string, string[]>;
  pageAnswer?: PageAnswer;
} {
  if (group.page) {
    const text =
      response.kind === "page"
        ? response.answer
        : response.kind === "question"
          ? (response.answers?.[PAGE_QUESTION_ID]?.join("\n").trim() ?? null)
          : undefined;
    if (text === undefined) throw new Error("Invalid response type.");
    const title = group.page.title;
    return text
      ? {
          body: `Answer to your page "${title}":\n${text}`,
          answers: { [PAGE_QUESTION_ID]: [text] },
          pageAnswer: { title },
        }
      : {
          body: `I skipped your page "${title}". Decide on your own, and say what you picked.`,
          answers: {},
          pageAnswer: { title, skipped: true as const },
        };
  }
  if (response.kind !== "question") throw new Error("Invalid response type.");
  // These questions don't hold the agent up; Dismiss hides them instead.
  const answers = response.answers;
  if (!answers) throw new Error("Answer each question before sending.");
  if (
    Object.keys(answers).some(
      (key) => !group.questions.some((q) => q.id === key),
    )
  )
    throw new Error("Unknown question.");
  if (group.questions.some((q) => !answers[q.id]?.some((a) => a.trim())))
    throw new Error("Answer each question before sending.");
  return {
    body:
      "Answer to your questions:\n" +
      group.questions
        .map((q) => `${q.question}\n${answers[q.id].join("\n")}`)
        .join("\n\n"),
    answers,
  };
}

/**
 * The secret a response to a secret request carries; undefined when the user
 * declined. A phone that shows the request as its hidden question answers
 * with that question, or skips it.
 */
export function secretGiven(response: AgentResponse): string | undefined {
  if (response.kind === "approval" || response.kind === "page")
    throw new Error("Invalid response type.");
  const typed =
    response.kind === "secret"
      ? response.value
      : (response.answers?.[SECRET_QUESTION_ID]?.join("") ?? null);
  if (typed === null) return undefined;
  const value = typed.trim();
  if (!value) throw new Error("Type the secret, or decline.");
  if (value.length > SECRET_MAX_CHARS)
    throw new Error("This is too long for a secret.");
  return value;
}

/** What the agent reads once the secret is in `file`. */
export const savedSecret = (
  secret: SecretRequest,
  file: string,
  always: boolean,
) => ({
  body: [
    `The user saved "${secret.name}" for you. It is in this private file, not in the chat:`,
    file,
    secretFileUse(file),
    ...(always
      ? [
          `They also chose to always give it in this project: ask for it under exactly this name again and it comes at once, with no card.`,
        ]
      : []),
  ].join("\n"),
  answers: { [SECRET_QUESTION_ID]: [always ? SECRET_ALWAYS : SECRET_SAVED] },
});

/** What the agent reads when the user declined; without it agents ask again in chat. */
export const declinedSecret = (secret: SecretRequest) => ({
  body: `The user declined to give "${secret.name}". Don't ask for it in chat or any other way; say what is blocked and go on without it.`,
  answers: {},
});

/** Answers message-based questions through the active turn, or a normal follow-up. */
export class AsyncQuestions {
  /** Beside state.json, so never inside a project or one of its worktrees. */
  private secrets: SecretFiles;

  constructor(
    private core: ChatCore,
    /** Sends while already holding the thread's control. */
    private send: (id: string, input: ProjectChatSend) => Promise<void>,
  ) {
    this.secrets = secretFiles(core.store.dir);
  }

  answer(id: string, messageId: string, itemId: string, value: AgentResponse) {
    return this.core.control(id, async () => {
      const { chat, message, group } = await this.question(
        id,
        messageId,
        itemId,
      );
      const response = agentResponseSchema.parse(value);
      const { body, answers, pageAnswer, file } = group.secret
        ? await this.secretAnswer(chat.projectId, group.secret, response)
        : { ...answerOf(group, response), file: undefined };
      if (body.length > 32000)
        throw new Error(
          "These answers are too long. Shorten them before sending.",
        );
      const answerMessageId = randomUUID();
      try {
        await this.deliver(chat, message, {
          id: answerMessageId,
          body,
          ...(pageAnswer ? { pageAnswer } : {}),
        });
      } catch (error) {
        // The agent never heard where it is, so nothing would read it.
        if (file) await this.secrets.remove(file).catch(() => {});
        throw error;
      }
      group.answers = answers;
      group.answerMessageId = answerMessageId;
      delete group.dismissed;
      message.version++;
      this.core.emit({ chatId: id, message: structuredClone(message) });
      await this.core.storage.save(chat);
    });
  }

  /**
   * Puts the secret the user gave into a file and words the answer around its
   * path, so the value reaches neither the agent's context nor the thread.
   */
  private async secretAnswer(
    projectId: string,
    secret: SecretRequest,
    response: AgentResponse,
  ) {
    const value = secretGiven(response);
    if (value === undefined)
      return {
        ...declinedSecret(secret),
        pageAnswer: undefined,
        file: undefined,
      };
    const always = response.kind === "secret" && !!response.remember;
    if (always)
      await rememberedSecrets(this.core.store).remember(
        projectId,
        secret.name,
        value,
      );
    const file = await this.secrets.save(value);
    return {
      ...savedSecret(secret, file, always),
      pageAnswer: undefined,
      file,
    };
  }

  /** Says the answer to the agent that asked: into its running turn when that one reads steers, otherwise as the next turn. */
  private async deliver(
    chat: ProjectChat,
    message: ChatMessage,
    answer: { id: string; body: string; pageAnswer?: PageAnswer },
  ) {
    await this.core.active.finished(chat.id);
    const active = this.core.active.get(chat.id);
    const sameConversation =
      active?.input &&
      agentAsked(active.input)?.provider === message.provider &&
      (active.input.parentId ?? null) === (message.parentId ?? null);
    const steer =
      active && sameConversation && !this.core.active.stopping(active)
        ? active.steer
        : undefined;
    if (!steer) {
      await this.send(chat.id, {
        ...sessionInput(
          chat,
          message.provider,
          this.core.store,
          message.parentId ?? undefined,
        ),
        ...answer,
        asyncQuestionAnswer: true,
        questionMessageId: message.id,
      });
      return;
    }
    const sent: ChatMessage = {
      ...answer,
      role: "user",
      provider: message.provider,
      status: "complete",
      created: Date.now(),
      version: 1,
      steered: true,
      asyncQuestionAnswer: true,
      questionMessageId: message.id,
      unread: true,
      ...(message.parentId ? { parentId: message.parentId } : {}),
    };
    // In the transcript before Codex echoes the steer it read.
    chat.messages.push(sent);
    try {
      await this.core.storage.save(chat);
      await steer(answer.body, sent.id);
    } catch (error) {
      chat.messages.splice(chat.messages.indexOf(sent), 1);
      await this.core.storage.save(chat);
      throw error;
    }
    this.core.emit({ chatId: chat.id, message: structuredClone(sent) });
  }

  setDismissed(
    id: string,
    messageId: string,
    itemId: string,
    dismissed: boolean,
  ) {
    return this.core.control(id, async () => {
      const { chat, message, group } = await this.question(
        id,
        messageId,
        itemId,
      );
      if (!!group.dismissed === dismissed) return;
      const before = group.dismissed;
      if (dismissed) group.dismissed = true;
      else delete group.dismissed;
      message.version++;
      try {
        await this.core.storage.save(chat);
      } catch (error) {
        if (before) group.dismissed = before;
        else delete group.dismissed;
        message.version++;
        throw error;
      }
      this.core.emit({ chatId: id, message: structuredClone(message) });
    });
  }

  private async question(id: string, messageId: string, itemId: string) {
    if (this.core.closing()) throw new Error("Relay is closing.");
    const chat = await this.core.storage.load(id);
    assertHere(chat);
    const message = chat.messages.find((m) => m.id === messageId);
    const group = message?.questions?.find((q) => q.id === itemId);
    if (!message || message.role !== "assistant" || !group)
      throw new Error("This question is no longer available.");
    if (group.answers)
      throw new Error("This question has already been answered.");
    return { chat, message, group };
  }
}
