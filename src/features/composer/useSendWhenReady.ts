import { useEffect, useState } from "react";
import { dictationSnapshot, stopDictation } from "../dictation/audio/session";
import type { ComposerDraft } from "./useComposerDraft";
import type { ComposerSendOptions } from "./useComposerSend";

interface HeldSend {
  options: ComposerSendOptions;
  /** The draft that was sent; the composer stays mounted across threads. */
  draftKey: string;
  waitedFor: "dictation" | "attachments";
}

/**
 * A send that comes before the draft is ready, mid-dictation or while a
 * pasted screenshot is prepared, waits for it and then goes from the next
 * render, which holds the last words or the screenshot.
 */
export function useSendWhenReady(
  draft: ComposerDraft,
  send: (options: ComposerSendOptions) => Promise<void>,
) {
  const [dictation] = useState(() => ({}));
  const [held, setHeld] = useState<HeldSend | null>(null);
  useEffect(() => {
    if (!held) return;
    setHeld(null);
    if (heldSendStillGoes(held, draft)) void send(held.options);
  }, [held]);
  return {
    /** Owns the dictation a send waits for. */
    dictation,
    /** Holds `options` back until the draft is ready; false when it already is. */
    heldBack(options: ComposerSendOptions) {
      const draftKey = draft.key;
      if (draft.isPreparing()) {
        void draft
          .settled()
          .then(() => setHeld({ options, draftKey, waitedFor: "attachments" }));
        return true;
      }
      const now = dictationSnapshot();
      if (now.owner !== dictation || now.phase === "idle") return false;
      void stopDictation().then((finished) => {
        if (finished) setHeld({ options, draftKey, waitedFor: "dictation" });
      });
      return true;
    },
  };
}

function heldSendStillGoes(held: HeldSend, draft: ComposerDraft) {
  // Another thread's draft is showing now; the one sent stays in its own.
  const sameDraft = held.draftKey === draft.key;
  // Each paste clears the error, so one now means it failed. The message
  // stays here with the error instead of going without its attachment.
  const attachmentFailed = held.waitedFor === "attachments" && !!draft.error;
  return sameDraft && !attachmentFailed;
}
