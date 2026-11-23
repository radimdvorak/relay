import { parseCodeReferences, type CodeReference } from "./code-references";
import { agentMention } from "./agents";
import { audioDataUrlBytes, maxAudioAttachments } from "./audio";
import { attachedImages, imagesAfter, nextImageNumber } from "./image-refs";
import { pastesAfter } from "./pasted-texts";
import type { ProjectChatSend } from "./projects";

/** A screenshot in a draft: `n` is its `[Image #n]` token, see image-refs. */
export type ReturnedImage = NonNullable<ProjectChatSend["images"]>[number] & {
  id: string;
  n?: number;
};

/** An audio clip restored from a queued message into a composer draft. */
export type ReturnedAudio = NonNullable<ProjectChatSend["audio"]>[number] & {
  id: string;
  sizeBytes: number;
};

/**
 * A queued message taken back into a draft that holds `draft`, `images` and
 * `audio`: it goes after the draft's text, its pastes and screenshots numbered
 * on from the draft's own. Throws when the two won't fit in one message. A
 * reply's code references stay in its text: side conversations attach none.
 * `newId` names the message's screenshots and audio clips as they join the draft.
 */
export function returnedDraft(
  draft: string,
  images: ReturnedImage[],
  input: Pick<ProjectChatSend, "body" | "images" | "audio" | "side">,
  reply: boolean,
  newId: () => string,
  audio: ReturnedAudio[] = [],
): {
  body: string;
  images: ReturnedImage[];
  audio: ReturnedAudio[];
  codeRefs: CodeReference[];
} {
  if (
    input.side &&
    (draft.trim() || attachedImages(draft, images).length || audio.length)
  )
    throw new Error(
      "Send or clear the current draft before restoring this side question.",
    );
  const incomingAudio = (input.audio ?? []).map((clip) => ({
    ...clip,
    id: newId(),
    sizeBytes: audioDataUrlBytes(clip.dataUrl),
  }));
  if (audio.length + incomingAudio.length > maxAudioAttachments)
    throw new Error(
      "Send or remove draft audio before restoring this message.",
    );
  const code = input.side
    ? {
        refs: [],
        body: `/btw ${agentMention(input.body)?.question ?? input.body}`,
      }
    : reply
      ? { refs: [], body: input.body }
      : parseCodeReferences(input.body);
  const back = imagesAfter(
    pastesAfter(draft, code.body),
    (input.images ?? []).map((image) => ({ ...image, id: newId() })),
    nextImageNumber(draft, images) - 1,
  );
  const body = [draft.trim(), back.text.trim()].filter(Boolean).join("\n\n");
  if (body.length > 32000)
    throw new Error(
      "Send or shorten the current draft before restoring this message.",
    );
  const restored = [...attachedImages(draft, images), ...back.images];
  return {
    body,
    images: restored,
    audio: [...audio, ...incomingAudio],
    codeRefs: code.refs,
  };
}
