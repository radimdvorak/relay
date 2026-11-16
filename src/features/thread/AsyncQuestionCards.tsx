import { useState } from "react";
import { agentName, type AgentProvider } from "../../../shared/agents";
import {
  PAGE_QUESTION_ID,
  type AsyncAgentQuestions,
  type ChatMessage,
} from "../../../shared/projects";
import type { AgentRequest, AgentResponse } from "../../../shared/agent-modes";
import { SECRET_QUESTION_ID } from "../../../shared/secret-request";
import { questionAnswers } from "../../../shared/asked-questions";
import { api } from "../../lib/api";
import { AnsweredQuestions } from "../agent-turn/AnsweredQuestions";
import { AgentRequestCard } from "./AgentRequestCard";
import { SecretRequestCard } from "./SecretRequestCard";
import { errorMessage } from "../../../shared/error-message";

/** Saved questions stay usable even after the turn's live trace folds away. */
export function AsyncQuestionCards({
  message,
  chatId,
  answerMessages,
}: {
  message: ChatMessage;
  chatId: string;
  answerMessages?: ReadonlyMap<string, ChatMessage>;
}) {
  if (!message.questions?.length || !chatId) return null;
  return (
    <div className="async-question-cards">
      {message.questions.map((group) =>
        group.answers ? (
          <AnsweredGroup
            key={group.id}
            group={group}
            answers={group.answers}
            status={answerStatus(group, message.provider, answerMessages)}
          />
        ) : (
          <AsyncQuestionCard
            key={group.id}
            group={group}
            messageId={message.id}
            provider={message.provider}
            chatId={chatId}
          />
        ),
      )}
    </div>
  );
}

/** The row an answered group folds into: what was asked and what the user said. */
function AnsweredGroup({
  group,
  answers,
  status,
}: {
  group: AsyncAgentQuestions;
  answers: Record<string, string[]>;
  status?: string;
}) {
  if (group.secret) {
    const saved = !!answers[SECRET_QUESTION_ID];
    return (
      <AnsweredQuestions
        status={status}
        label={`${saved ? "Saved" : "Declined"} "${group.secret.name}"`}
        note={saved ? "In a private file, not in the chat" : undefined}
      />
    );
  }
  if (group.page) {
    const answer = answers[PAGE_QUESTION_ID];
    return (
      <AnsweredQuestions
        status={status}
        label={`${answer ? "Answered" : "Skipped"} "${group.page.title}"`}
        questions={[
          {
            question: group.questions[0]?.question ?? group.page.title,
            answer: answer?.join("\n") ?? "Skipped",
          },
        ]}
      />
    );
  }
  return (
    <AnsweredQuestions
      questions={questionAnswers(group.questions, answers)}
      status={status}
    />
  );
}

function answerStatus(
  group: AsyncAgentQuestions,
  provider: AgentProvider,
  messages: ReadonlyMap<string, ChatMessage> | undefined,
) {
  const reply = group.answerMessageId
    ? messages?.get(group.answerMessageId)
    : undefined;
  if (reply?.missed) return "Not read · turn stopped";
  if (reply?.unread) return `Sent · awaiting ${agentName(provider)}`;
}

/** The card a group shows as: a page the agent asked with, or its questions. */
function requestOf(
  group: AsyncAgentQuestions,
  provider: AgentProvider,
): AgentRequest {
  if (group.page) {
    const ask = group.questions[0]?.question;
    return {
      id: group.id,
      kind: "page",
      title: group.page.title,
      ...(ask && ask !== group.page.title ? { detail: ask } : {}),
      page: group.page,
    };
  }
  return {
    id: group.id,
    kind: "question",
    title: `${agentName(provider)} has ${group.questions.length === 1 ? "a question" : "questions"}`,
    questions: group.questions,
  };
}

/** An open group's card: a secret's hidden field, or the page or questions asked. */
function OpenCard({
  group,
  messageId,
  provider,
  chatId,
}: {
  group: AsyncAgentQuestions;
  messageId: string;
  provider: AgentProvider;
  chatId: string;
}) {
  const answer = (response: AgentResponse) =>
    api.answerProjectChatQuestion(chatId, messageId, group.id, response);
  return group.secret ? (
    <SecretRequestCard
      secret={group.secret}
      agent={agentName(provider)}
      onRespond={answer}
    />
  ) : (
    <AgentRequestCard
      deferred
      chatId={chatId}
      request={requestOf(group, provider)}
      onRespond={answer}
      onDismiss={() =>
        api.setProjectChatQuestionDismissed(chatId, messageId, group.id, true)
      }
    />
  );
}

function AsyncQuestionCard({
  group,
  messageId,
  provider,
  chatId,
}: {
  group: AsyncAgentQuestions;
  messageId: string;
  provider: AgentProvider;
  chatId: string;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  async function reopen() {
    setBusy(true);
    setError(undefined);
    try {
      await api.setProjectChatQuestionDismissed(
        chatId,
        messageId,
        group.id,
        false,
      );
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      {/* Keep a partly written answer when the user dismisses and reopens. */}
      <div hidden={!!group.dismissed}>
        <OpenCard
          group={group}
          messageId={messageId}
          provider={provider}
          chatId={chatId}
        />
      </div>
      {group.dismissed && (
        <div className="async-question-dismissed">
          <details className="async-question-answered">
            <summary>
              Dismissed{" "}
              {group.questions.length === 1 ? "question" : "questions"}
            </summary>
            {group.questions.map((q) => (
              <p key={q.id}>{q.question}</p>
            ))}
          </details>
          <button
            type="button"
            className="text-button"
            disabled={busy}
            onClick={() => void reopen()}
          >
            Reopen
          </button>
        </div>
      )}
      {error && <p role="alert">{error}</p>}
    </>
  );
}
