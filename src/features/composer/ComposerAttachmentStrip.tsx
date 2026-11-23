import { X } from "lucide-react";
import { Spinner } from "../../ui/ui";
import { flattenSketch } from "../images/sketch";
import type { ComposerDraft } from "./useComposerDraft";
import { CopyImageMenu } from "../images/CopyImageMenu";
import { SketchOverlay } from "../images/ImageSketch";
import { PastedTextCard } from "./PastedTextCard";
import "../images/image-tiles.css";
import { BrowserAnnotationChip } from "../images/BrowserAnnotationChip";
import { AudioTile } from "../audio/AudioTile";

function ImageAttachmentTile({
  image,
  draft,
}: {
  image: ComposerDraft["attached"][number];
  draft: ComposerDraft;
}) {
  if (image.browser)
    return (
      <BrowserAnnotationChip
        annotation={image.browser}
        image={{
          key: `draft:${image.id}`,
          name: image.name,
          load: () => Promise.resolve(image.dataUrl),
        }}
        onRemove={() => draft.removeImage(image)}
        removeDisabled={draft.preparing}
      />
    );
  return (
    <CopyImageMenu
      className="composer-image"
      source={async () => (await flattenSketch(image)).dataUrl}
    >
      <button
        type="button"
        className="composer-image-open"
        aria-label={`Draw on ${image.name}`}
        title="Draw on screenshot"
        onClick={() => draft.sketch(image.id)}
      >
        <img src={image.dataUrl} alt={image.name} />
        {image.sketch && (
          <SketchOverlay sketch={image.sketch} src={image.dataUrl} />
        )}
      </button>
      <button
        type="button"
        className="composer-image-remove"
        disabled={draft.preparing}
        aria-label={`Remove ${image.name}`}
        onClick={() => draft.removeImage(image)}
      >
        <X size={13} />
      </button>
    </CopyImageMenu>
  );
}

/** The draft's screenshots, audio clips, and long pastes, as cards above the input. */
export function ComposerAttachmentStrip({
  draft,
  onOpenPaste,
  onRemovePaste,
}: {
  draft: ComposerDraft;
  onOpenPaste: (index: number) => void;
  onRemovePaste: (index: number) => void;
}) {
  const { attached, audio, pastes, incoming } = draft;
  if (!attached.length && !audio.length && !pastes.length && !incoming)
    return null;
  return (
    <div className="composer-images" aria-label="Attachments">
      {attached.map((image) => (
        <ImageAttachmentTile key={image.id} image={image} draft={draft} />
      ))}
      {audio.map((clip) => (
        <AudioTile
          key={clip.id}
          name={clip.name}
          mimeType={clip.mimeType}
          durationSec={clip.durationSec}
          peaks={clip.peaks}
          src={clip.dataUrl}
          onRemove={() => draft.removeAudio(clip)}
          removeDisabled={draft.preparing}
        />
      ))}
      {Array.from({ length: incoming }, (_, i) => (
        <div
          key={`incoming-${i}`}
          className="composer-image incoming"
          role="status"
          aria-label="Preparing attachment"
        >
          <Spinner size={16} />
        </div>
      ))}
      {pastes.map((paste, index) => (
        <PastedTextCard
          key={index}
          paste={paste}
          onOpen={() => onOpenPaste(index)}
          onRemove={() => onRemovePaste(index)}
        />
      ))}
    </div>
  );
}
