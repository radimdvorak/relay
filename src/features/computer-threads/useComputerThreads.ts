import { useQuery } from "@tanstack/react-query";
import { api } from "../../lib/api";
import { usePoll } from "../../lib/poll";

/** Each paired computer's own threads; `watch` keeps them fresh while they show. */
export function useComputerThreads(watch: boolean) {
  const poll = usePoll();
  return useQuery({
    queryKey: ["computer-threads"],
    queryFn: () => api.computerThreads(),
    staleTime: 3000,
    ...(watch ? { refetchInterval: poll(5000) } : {}),
  });
}
