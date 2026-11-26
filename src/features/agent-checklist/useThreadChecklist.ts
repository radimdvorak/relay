import { useQuery, useQueryClient } from "@tanstack/react-query";
import { threadChecklist } from "../../../shared/agent-checklist";
import type { ProjectChat } from "../../../shared/projects";
import { chatKey, fetchChat } from "../../lib/chat-events";

const checklistIn = (chat: ProjectChat) => threadChecklist(chat.messages);

/**
 * The checklist the thread's agent is on, read from the thread's cached copy. The
 * thread itself keeps that copy fresh, so this never fetches; it changes only
 * when the checklist does, not with every streamed piece.
 */
export function useThreadChecklist(chatId: string | undefined) {
  const qc = useQueryClient();
  return useQuery({
    queryKey: chatKey(chatId),
    queryFn: () => fetchChat(qc, chatId!),
    enabled: false,
    select: checklistIn,
    notifyOnChangeProps: ["data"],
  }).data;
}
