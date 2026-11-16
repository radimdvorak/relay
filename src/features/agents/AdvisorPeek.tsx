import { useCallback, useId, useSyncExternalStore } from "react";
import { NO_ADVISOR } from "../../../shared/claude-advisor";
import { composerSettingsKey } from "../../lib/thread-storage";
import {
  loadComposerSettings,
  onComposerSettingsChanged,
} from "./composer-settings";
import { useClaudeModels } from "./useClaudeModels";
import "./advisor-peek.css";

/** A nerd raising a finger: the advisor, about to say "well, actually". */
export function AdvisorIcon({ size = 15 }: { size?: number }) {
  // Masks are found by id; each icon on the page needs its own.
  const id = useId();
  const hand = `${id}-hand`,
    teeth = `${id}-teeth`;
  const handPath =
    "M5.6 12.6V7.6a1.2 1.2 0 0 1 2.4 0v5.2c.7.5 1.1 1.3 1.1 2.2v3.2a3.2 3.2 0 0 1-3.2 3.2H4.2A3.2 3.2 0 0 1 1 18.2v-3.4a.77.77 0 0 1 1.53 0v-.6a.77.77 0 0 1 1.53 0V13.6a.77.77 0 0 1 1.54 0Z";
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 .5 24 24"
      fill="none"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <mask id={hand}>
        <rect width="24" height="24" fill="#fff" stroke="none" />
        <path d={handPath} fill="#000" stroke="#000" strokeWidth="3.2" />
      </mask>
      <mask id={teeth}>
        <rect width="24" height="24" fill="#fff" stroke="none" />
        <path
          d="M14.05 13.6h1.45v2.1h-1.45zM15.7 13.6h1.45v2.1H15.7z"
          fill="#000"
          stroke="none"
        />
      </mask>
      <g mask={`url(#${hand})`} strokeWidth="1.5">
        <circle cx="15.6" cy="11.6" r="7.6" />
        <rect x="10.4" y="7.3" width="4.3" height="3.9" rx="1.4" />
        <rect x="16.5" y="7.3" width="4.3" height="3.9" rx="1.4" />
        <path d="M14.7 8.8h1.8" />
        <circle cx="12.55" cy="9.3" r=".7" fill="currentColor" stroke="none" />
        <circle cx="18.65" cy="9.3" r=".7" fill="currentColor" stroke="none" />
        <path
          d="M12.3 13.6h6.6a3.3 3.3 0 0 1-6.6 0z"
          fill="currentColor"
          strokeWidth="1"
          mask={`url(#${teeth})`}
        />
      </g>
      <path d={handPath} strokeWidth="1.3" />
      <path
        d="M9.1 16.4C7.4 16.4 6.1 15.8 4.8 15.8a1.1 1.1 0 0 0-.4 2.1L6.8 19"
        strokeWidth="1.1"
      />
    </svg>
  );
}

/** The advisor the conversation under `settingsKey` picked for Claude, as its composer keeps it. */
function usePickedAdvisor(settingsKey: string) {
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
    const settings = loadComposerSettings(settingsKey);
    if (settings.provider !== "claude") return undefined;
    const advisor = settings.models.claude?.advisor;
    return advisor === NO_ADVISOR ? undefined : advisor;
  };
  return useSyncExternalStore(subscribe, snapshot, snapshot);
}

/** "Opus 5.5" as Claude lists it; "Opus" before its list loads. */
function useAdvisorName(alias: string | undefined) {
  const { models } = useClaudeModels();
  if (!alias) return "";
  const listed = models?.find((m) => m.id === alias);
  return listed?.name ?? alias.charAt(0).toUpperCase() + alias.slice(1);
}

/**
 * Claude's advisor, peeking over the composer's top edge while the thread
 * has one. Hovered, it comes up to say who it is.
 */
export function AdvisorPeek({ settingsKey }: { settingsKey: string }) {
  const advisor = usePickedAdvisor(settingsKey);
  const name = useAdvisorName(advisor);
  if (!advisor) return null;
  return (
    <div className="advisor-peek">
      <span className="advisor-peek-say">
        Erm, actually… {name} advises. /advisor to change
      </span>
      <span className="advisor-peek-edge" aria-label={`${name} advises`}>
        <AdvisorIcon size={26} />
      </span>
    </div>
  );
}
