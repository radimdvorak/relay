import { useEffect, useMemo, useState, type RefObject } from "react";
import { agentMentionPattern } from "../../../shared/agents";
import { pastedTexts } from "../../../shared/pasted-texts";
import {
  canReceiveAudio,
  draftRecipient,
  type Recipient,
} from "../../../shared/recipient";
import { pickedSkills } from "../../../shared/skill-mentions";
import { unsent } from "../../../shared/drafts";
import { draftChips } from "../../lib/thread-storage";
import type { DraftImage } from "../images/draft-images";
import { useImagePills } from "../images/image-pills";
import { readDraft, useDraft, writeDraft } from "./drafts";
import { draftIntake, PASTE_TOO_LONG, type DraftEditor } from "./draft-intake";
import { useDraftAudio } from "./useDraftAudio";
import { useDraftImages } from "./useDraftImages";
import { usePreparationQueue } from "./usePreparationQueue";

export type { DraftEditor } from "./draft-intake";
export type ComposerDraft = ReturnType<typeof useComposerDraft>;

function takeDraft({
  key,
  text,
  set,
  screenshots,
  audio,
  withAttachments,
}: {
  key: string;
  text: string;
  set: (value: string) => void;
  screenshots: ReturnType<typeof useDraftImages>;
  audio: ReturnType<typeof useDraftAudio>;
  withAttachments: boolean;
}) {
  let taken: string | undefined;
  const outgoingImages = withAttachments ? screenshots.take() : undefined;
  const outgoingAudio = withAttachments ? audio.take() : undefined;
  return {
    dispatch() {
      taken = text;
      // Typed while the message was being readied: it stays for the next one.
      set(unsent(readDraft(key), text.trim()));
      outgoingImages?.dispatch();
      outgoingAudio?.dispatch();
    },
    restore() {
      if (taken === undefined) return;
      const typed = readDraft(key).trim();
      set(typed ? `${taken.trimEnd()}\n\n${typed}` : taken);
      outgoingImages?.restore();
      outgoingAudio?.restore();
    },
    /** The message went: its draft copies go, any added since stay. */
    async forget() {
      await Promise.all([outgoingImages?.forget(), outgoingAudio?.forget()]);
    },
  };
}

/** The draft's text, persisted screenshots, audio clips, and incoming attachments. */
export function useComposerDraft(
  key: string,
  editor: RefObject<DraftEditor | null>,
  provider: Recipient = "message",
) {
  const text = useDraft(key);
  const set = (value: string) => writeDraft(key, value);
  const [error, setError] = useState<string>();
  const { queuePreparation, ...preparation } = usePreparationQueue(setError);
  const screenshots = useDraftImages(key, text, setError);
  const audio = useDraftAudio(key, setError);
  const imagePills = useImagePills();
  const to = draftRecipient(text, provider);
  const acceptsAudio = canReceiveAudio(to);
  // Paste pills live in the draft text; their cards mirror them in order.
  const pastes = useMemo(() => pastedTexts(text), [text]);
  // A refused paste leaves the draft as it was; the next edit moves on from it.
  useEffect(() => {
    setError((error) => (error === PASTE_TOO_LONG ? undefined : error));
  }, [text]);
  const intake = draftIntake({
    key,
    editor,
    screenshots,
    audio,
    acceptsAudio,
    imagePills,
    setError,
    queuePreparation,
  });
  return {
    key,
    text,
    set,
    images: screenshots.images,
    attached: screenshots.attached,
    audio: audio.audio,
    hasAttachments: screenshots.attached.length > 0 || audio.audio.length > 0,
    chips: screenshots.chips,
    pastes,
    /** What went wrong bringing something in, or sending it. */
    error,
    setError,
    ...intake,
    ...preparation,
    sketching: screenshots.sketching,
    sketch: screenshots.sketch,
    historyOf: screenshots.historyOf,
    finishSketch: screenshots.finishSketch,
    /** An agent was picked, so an @mention would only override it. */
    dropMention() {
      const prefix = agentMentionPattern.exec(text.trimStart())?.[0];
      if (prefix)
        editor.current?.insertText({
          start: 0,
          end: text.length - text.trimStart().length + prefix.length,
          text: "",
        });
    },
    removeImage(image: DraftImage) {
      if (image.n !== undefined) editor.current?.removeImage(image.n);
      screenshots.removeImage(image);
    },
    removeAudio: audio.removeAudio,
    clearAudio: audio.clearAudio,
    /** The skills picked as pills that `text` still mentions. */
    skills: (text: string) => pickedSkills(text, draftChips(key).skills.load()),
    /**
     * Empties the composer as the message goes out; a message that is turned
     * down comes back, ahead of anything typed since.
     */
    take: (withAttachments: boolean) =>
      takeDraft({ key, text, set, screenshots, audio, withAttachments }),
  };
}
