import { useReducer } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { TerminalSession } from "../../../shared/terminal-sessions";
import { api } from "../../lib/api";

/** A listing older than this is looked up again when the menu asks. */
const FRESH_MS = 15_000;

/** The saved Claude Code and Codex sessions run in the project's folder. */
export function useSessionListing(
  projectId: string,
  onError: (error: unknown) => void,
) {
  const qc = useQueryClient();
  const [, bump] = useReducer((n: number) => n + 1, 0);
  const key = ["terminal-sessions", projectId];
  return {
    /** The sessions listed so far; asking looks them up again once old. */
    current(): TerminalSession[] | undefined {
      const state = qc.getQueryState<TerminalSession[]>(key);
      if (
        state?.fetchStatus !== "fetching" &&
        (!state || Date.now() - state.dataUpdatedAt > FRESH_MS)
      )
        void qc
          .query({
            queryKey: key,
            queryFn: () => api.terminalSessions(projectId),
            staleTime: FRESH_MS,
          })
          .catch(onError)
          .then(bump);
      return state?.data;
    },

    /** The sessions listed so far, without looking again. */
    listed: () => qc.getQueryData<TerminalSession[]>(key) ?? [],
    /** Forgets the listing, so the next one is looked up. */
    forget: () => qc.removeQueries({ queryKey: key }),
  };
}
