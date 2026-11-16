import { memo, useCallback, useSyncExternalStore } from "react";
import { useQuery } from "@tanstack/react-query";
import { X } from "lucide-react";
import { NO_ADVISOR, type Advisor } from "../../../shared/advisor";
import type { AgentModel, AgentProvider } from "../../../shared/agents";
import { composerSettingsKey } from "../../lib/thread-storage";
import {
  loadComposerSettings,
  onComposerSettingsChanged,
} from "./composer-settings";
import { AdvisorIcon } from "./AdvisorIcon";
import { agentModelsQuery } from "./useAgentPicks";
import "./advisor-peek.css";

/**
 * The advisor of the agent the conversation under `settingsKey` runs, as its
 * composer keeps it: "provider model", a string so the store's snapshot
 * stays the same between changes.
 */
function usePickedAdvisor(settingsKey: string): Advisor | undefined {
  const subscribe = useCallback(
    (listener: () => void) => {
      const unsubscribe = onComposerSettingsChanged(settingsKey, listener);
      const heard = (event: StorageEvent) => {
        if (
          event.key === null ||
          event.key === composerSettingsKey(settingsKey)
        )
          listener();
      };
      window.addEventListener("storage", heard);
      return () => {
        unsubscribe();
        window.removeEventListener("storage", heard);
      };
    },
    [settingsKey],
  );
  const snapshot = () => {
    const { provider, models } = loadComposerSettings(settingsKey);
    if (!provider || provider === "message") return undefined;
    const advisor = models[provider]?.advisor;
    if (!advisor || advisor === NO_ADVISOR) return undefined;
    return `${advisor.provider} ${advisor.model}`;
  };
  const picked = useSyncExternalStore(subscribe, snapshot, snapshot);
  if (!picked) return undefined;
  const [provider, ...model] = picked.split(" ");
  return { provider: provider as AgentProvider, model: model.join(" ") };
}

/** "Opus 5.5" as its agent lists it; the model's id before the list loads. */
function useAdvisorName(advisor: Advisor | undefined) {
  const listed = useQuery({
    ...agentModelsQuery(advisor?.provider ?? "claude"),
    enabled: !!advisor,
  }).data as readonly AgentModel[] | undefined;
  if (!advisor) return "";
  const model = listed?.find((m) => m.id === advisor.model);
  return model?.name ?? advisor.model;
}

/**
 * The thread's advisor, peeking over the composer's top edge while its agent
 * has one. Hovered, it comes up to say who it is, with a way to turn it off. Memoized: the composer
 * renders on every streamed piece, and the advisor doesn't change with them.
 */
export const AdvisorPeek = memo(function AdvisorPeek({
  settingsKey,
  onStop,
}: {
  settingsKey: string;
  onStop: () => void;
}) {
  const advisor = usePickedAdvisor(settingsKey);
  const name = useAdvisorName(advisor);
  if (!advisor) return null;
  return (
    <div className="advisor-peek">
      <span className="advisor-peek-say">
        Erm, actually… {name} advises. /advisor to change
        <button
          type="button"
          className="advisor-peek-stop"
          aria-label="Turn off the advisor"
          title="Turn off the advisor"
          onClick={onStop}
        >
          <X size={12} />
        </button>
      </span>
      <span className="advisor-peek-edge" aria-label={`${name} advises`}>
        <AdvisorIcon />
      </span>
    </div>
  );
});
