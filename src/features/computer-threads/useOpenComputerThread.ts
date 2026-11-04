import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "../../lib/api";
import { openThreadByIds } from "../../lib/open-thread";

/**
 * Opens one of a paired computer's own threads here. The first time it's
 * followed from here, which takes a moment: `opening` is that thread's id.
 */
export function useOpenComputerThread() {
  const qc = useQueryClient();
  const [error, setError] = useState<unknown>();
  const [opening, setOpening] = useState<string>();
  const open = (computerId: string, threadId: string) => {
    setError(undefined);
    setOpening(threadId);
    api
      .openComputerThread(computerId, threadId)
      .then((here) => {
        openThreadByIds(here);
        // The list now knows the thread here that follows it.
        void qc.invalidateQueries({ queryKey: ["computer-threads"] });
      })
      .catch(setError)
      .finally(() => setOpening(undefined));
  };
  return { open, opening, error };
}
