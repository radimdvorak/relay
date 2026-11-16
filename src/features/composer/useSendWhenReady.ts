import { useEffect, useState } from "react";
import { dictationSnapshot, stopDictation } from "../dictation/audio/session";
import type { ComposerSendOptions } from "./useComposerSend";

/**
 * A send that comes mid-dictation waits for the last words, then goes from
 * the next render, which holds them in the draft.
 */
export function useSendWhenReady(
  send: (options: ComposerSendOptions) => Promise<void>,
) {
  const [dictation] = useState(() => ({}));
  const [held, setHeld] = useState<ComposerSendOptions | null>(null);
  useEffect(() => {
    if (!held) return;
    setHeld(null);
    void send(held);
  }, [held]);
  return {
    /** Owns the dictation a send waits for. */
    dictation,
    /** Holds `options` back until the draft is ready; false when it already is. */
    heldBack(options: ComposerSendOptions) {
      const now = dictationSnapshot();
      if (now.owner !== dictation || now.phase === "idle") return false;
      void stopDictation().then((finished) => {
        if (finished) setHeld(options);
      });
      return true;
    },
  };
}
