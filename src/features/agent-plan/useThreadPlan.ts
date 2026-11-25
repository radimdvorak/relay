import { useQuery, useQueryClient } from "@tanstack/react-query";
import { threadPlan } from "../../../shared/agent-plan";
import type { ProjectChat } from "../../../shared/projects";
import { chatKey, fetchChat } from "../../lib/chat-events";

const planIn = (chat: ProjectChat) => threadPlan(chat.messages);

/**
 * The plan the thread's agent is on, read from the thread's cached copy. The
 * thread itself keeps that copy fresh, so this never fetches; it changes only
 * when the plan does, not with every streamed piece.
 */
export function useThreadPlan(chatId: string | undefined) {
  const qc = useQueryClient();
  return useQuery({
    queryKey: chatKey(chatId),
    queryFn: () => fetchChat(qc, chatId!),
    enabled: false,
    select: planIn,
    notifyOnChangeProps: ["data"],
  }).data;
}
