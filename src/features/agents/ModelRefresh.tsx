import { useState } from "react";
import { useQueryClient, type QueryKey } from "@tanstack/react-query";
import { RotateCw } from "lucide-react";
import { agentName, type AgentProvider } from "../../../shared/agents";
import { errorMessage } from "../../../shared/error-message";
import { api } from "../../lib/api";
import { Spinner } from "../../ui/ui";
import type { Category } from "./model-picker-catalog";

type Refreshable = Extract<AgentProvider, "codex" | "claude">;

/** Agents whose list Relay keeps, and can ask for again without restarting them. */
export const refreshesModels = (category: Category): category is Refreshable =>
  category === "codex" || category === "claude";

type State =
  | { kind: "idle" }
  | { kind: "busy" }
  | { kind: "done"; added: number }
  | { kind: "failed"; error: string };

/** Where the renderer keeps the agent's list: Codex has its own query besides the shared one. */
function listKeys(provider: Refreshable, computerId?: string): QueryKey[] {
  const there = computerId ? [computerId] : [];
  const shared = ["agent-models", provider, ...there];
  return provider === "codex" ? [["codex-models", ...there], shared] : [shared];
}

/** Asks the agent for its models now, e.g. right after a model launches. */
export function ModelRefresh({
  provider,
  computer,
}: {
  provider: Refreshable;
  computer?: { id: string; name: string };
}) {
  const qc = useQueryClient();
  const [state, setState] = useState<State>({ kind: "idle" });
  const keys = listKeys(provider, computer?.id);
  const ids = () =>
    new Set(
      keys.flatMap(
        (key) => qc.getQueryData<{ id: string }[]>(key)?.map((m) => m.id) ?? [],
      ),
    );

  async function refresh() {
    const before = ids();
    setState({ kind: "busy" });
    try {
      await api.reloadAgentModels(provider, false, computer?.id);
      await Promise.all(
        keys.map((queryKey) => qc.refetchQueries({ queryKey, exact: true })),
      );
      const added = [...ids()].filter((id) => !before.has(id)).length;
      setState({ kind: "done", added });
    } catch (error) {
      setState({ kind: "failed", error: errorMessage(error) });
    }
  }

  return (
    <div className="model-refresh">
      <span role="status" title={state.kind === "failed" ? state.error : ""}>
        {statusText(state)}
      </span>
      <button
        type="button"
        className="model-refresh-button"
        aria-label={`Ask ${agentName(provider)} for its models again`}
        title="Refresh models"
        disabled={state.kind === "busy"}
        onClick={() => void refresh()}
      >
        {state.kind === "busy" ? (
          <Spinner size={13} />
        ) : (
          <RotateCw size={13} aria-hidden />
        )}
      </button>
    </div>
  );
}

function statusText(state: State) {
  if (state.kind === "failed") return "Couldn't refresh";
  if (state.kind !== "done") return "";
  if (!state.added) return "Up to date";
  return `${state.added} new model${state.added === 1 ? "" : "s"}`;
}
