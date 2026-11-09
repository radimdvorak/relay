import { useQuery } from "@tanstack/react-query";
import type { ChatSummary } from "../../../shared/projects";
import { api } from "../../lib/api";

/**
 * What the thread's session and other sessions told each other, fetched
 * again whenever its listed summary says another came.
 */
export function usePeerMessages(
  chat: Pick<ChatSummary, "id" | "peerMark"> | undefined,
) {
  const id = chat?.id;
  const mark = chat?.peerMark;
  const query = useQuery({
    queryKey: ["peer-messages", id, mark],
    queryFn: () => api.threadPeerMessages(id!),
    enabled: !!id && !!mark,
    staleTime: Infinity,
    placeholderData: (previous) => previous,
  });
  return mark ? query.data : undefined;
}
