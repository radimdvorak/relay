import {
  reviewedElsewhereLabel,
  type DeepReviewStart,
} from "../../../shared/deep-review";
import { errorMessage } from "../../../shared/error-message";
import { fixRequest, type Finding } from "../../../shared/findings";
import type { AgentProvider } from "../../../shared/agents";
import {
  lastComposerSettings,
  type ChatSummary,
  type LinkedFolder,
  type ProjectChat as ProjectChatData,
  type ProjectChatSend,
} from "../../../shared/projects";
import { api } from "../../lib/api";
import { ErrorWithAction } from "../../lib/error-action";
import {
  loadComposerSettings,
  saveSentSettings,
  withSent,
} from "../agents/composer-settings";
import { modelOf } from "../agents/composer-models";
import { useNewThread } from "./useNewThread";
import type { ThreadHandle } from "./useThreadHandle";
import { noteUsed } from "../../lib/used";

export type Councils = ReturnType<typeof useCouncils>;

/** A thread's deep review and the findings its answers published, and what the thread can do with them. */
export function useCouncils({
  handle: { chat, projectId, run, setError, refetch, listChanged },
  data,
  draftLinks,
  onCreated,
  onSent,
}: {
  handle: ThreadHandle;
  data?: ProjectChatData;
  draftLinks?: LinkedFolder[];
  onCreated: (c: ChatSummary) => Promise<void>;
  /** Something went to the agents; the thread follows the answer. */
  onSent: () => void;
}) {
  const reviews = data?.reviews;
  // Stop, Resume and fixes go to the latest round.
  const review = reviews?.at(-1);
  // Reviewers work in threads of their own; this one waits for the lead.
  const reviewing = review?.status === "reviewing";
  // A start that failed leaves its thread for the next try. Kept apart from
  // the composer's, so a message sent from this draft instead gets a thread of its own.
  const reviewThread = useNewThread(
    () =>
      api.createProjectChat(
        projectId,
        { kind: "review" },
        undefined,
        undefined,
        draftLinks,
      ),
    onCreated,
  );
  async function startReview(config: DeepReviewStart) {
    return run(async () => {
      await reviewThread(async (thread) => {
        // Messages in the thread go to the lead, with the lead's settings.
        const { lead } = config;
        saveSentSettings(thread.id, lead.provider, {
          ...lead,
          runtimeMode: config.runtimeMode,
          interactionMode: "default",
        });
        await api.startDeepReview(thread.id, config);
        noteUsed("deep-review");
        onSent();
      });
      await listChanged();
    });
  }
  /**
   * Straight to the agent that reported them, a review's lead on the lead's
   * settings; whatever the composer holds stays there. Refused because the
   * reviewed code isn't checked out here, it offers to check it out first.
   */
  async function fixFindings(findings: Finding[], reporter: AgentProvider) {
    if (!chat || !findings.length) return;
    const message = () => fixMessage(chat.id, data, findings, reporter);
    const send = async (checkOut: boolean) => {
      const ids = findings.map((f) => f.id);
      if (checkOut) await api.checkOutReviewed(chat.id, ids);
      await api.sendProjectChat(chat.id, message());
      onSent();
      await refetch();
      if (checkOut) await listChanged();
    };
    await run(() =>
      send(false).catch((e: unknown) => {
        throw offerCheckout(e, () => void run(() => send(true)));
      }),
    );
  }
  function setFindingStatus(id: string, status: "open" | "dismissed") {
    if (!chat) return;
    void api
      .setFinding(chat.id, id, status)
      .then(() => refetch())
      .catch(setError);
  }
  function resume(start: (chatId: string) => Promise<unknown>) {
    if (!chat) return;
    void run(async () => {
      await start(chat.id);
      await refetch();
    });
  }
  return {
    review,
    reviews,
    findings: data?.findings,
    reviewing,
    startReview,
    fixFindings,
    setFindingStatus,
    resumeReview: () => resume((chatId) => api.resumeDeepReview(chatId)),
    // The thread's Stop: it stops the reviewers along with anything else running.
    stopReview: () => {
      if (chat) void api.cancelProjectChat(chat.id).catch(setError);
    },
    // A reviewer's own Stop, as in its thread.
    stopReviewer: (slot: number) => {
      const member = review?.reviewers[slot];
      if (member) void api.cancelProjectChat(member.chatId).catch(setError);
    },
    rerunReviewer: (slot: number) =>
      resume((chatId) => api.resumeDeepReview(chatId, { slot })),
    leadWithReports: () =>
      resume((chatId) => api.resumeDeepReview(chatId, { lead: true })),
  };
}

/** A fix of `findings`, to the agent that reported them or the review's lead, on its settings. */
function fixMessage(
  chatId: string,
  data: ProjectChatData | undefined,
  findings: Finding[],
  reporter: AgentProvider,
): ProjectChatSend {
  const review = data?.reviews?.at(-1);
  const saved = review
    ? undefined
    : withSent(loadComposerSettings(chatId), lastComposerSettings(data));
  const provider = review?.lead.provider ?? reporter;
  const model = saved && modelOf(saved.models, provider);
  return {
    id: crypto.randomUUID(),
    body: fixRequest(provider, findings),
    to: provider,
    provider,
    choice: review?.lead.choice ?? model!.choice,
    ...(model?.contextWindow ? { contextWindow: model.contextWindow } : {}),
    runtimeMode: review?.runtimeMode ?? saved!.runtimeMode,
    interactionMode: "default",
    fixes: findings.map((f) => f.id),
  };
}

/** A refusal because the reviewed code isn't checked out here, with a button that checks it out and fixes. */
function offerCheckout(error: unknown, checkOutAndFix: () => void) {
  const message = errorMessage(error);
  const label = reviewedElsewhereLabel(message);
  if (!label) return error;
  return new ErrorWithAction(message, {
    label: `Check out ${label} and fix`,
    run: checkOutAndFix,
  });
}
