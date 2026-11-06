import { useReducer } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { TerminalSession } from "../../../shared/terminal-sessions";
import { api } from "../../lib/api";

/** A listing older than this is looked up again when the menu asks. */
const FRESH_MS = 15_000;

/** The project's saved terminal and app sessions, looked up when the menu asks. */
export function useSessionListing(
  projectId: string,
  onError: (error: unknown) => void,
) {
  const qc = useQueryClient();
  const [, bump] = useReducer((n: number) => n + 1, 0);
  const key = ["terminal-sessions", projectId];

  function sessions(): TerminalSession[] | undefined {
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
  }

  return {
    sessions,
    /** The last listing, without looking again. */
    listed: () => qc.getQueryData<TerminalSession[]>(key) ?? [],
    /** The next listing should look again, e.g. once a session is in Relay. */
    forget: () => qc.removeQueries({ queryKey: key }),
  };
}
