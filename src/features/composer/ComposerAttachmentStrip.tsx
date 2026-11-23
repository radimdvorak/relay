import { AlertTriangle, X } from "lucide-react";
import { agentName, type AgentProvider } from "../../../shared/agents";
import {
  canReceiveAudio,
  defaultAudioAgent,
  type Recipient,
} from "../../../shared/recipient";
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

function AudioUnsupportedWarning({
  to,
  disabled,
  onSwitchAgent,
  onClearAudio,
}: {
  to: Recipient;
  disabled: boolean;
  onSwitchAgent?: (provider: AgentProvider) => void;
  onClearAudio: () => void;
}) {
  const audioAgent = defaultAudioAgent();
  const reason =
    to === "message"
      ? "Notes can't include audio."
      : `${agentName(to)} can't listen to audio.`;

  return (
    <div className="composer-audio-warning" role="alert">
      <AlertTriangle
        size={14}
        className="composer-audio-warning-icon"
        aria-hidden
      />
      <span className="composer-audio-warning-text">{reason}</span>
      <div className="composer-audio-warning-actions">
        {audioAgent && onSwitchAgent && (
          <button
            type="button"
            disabled={disabled}
            onClick={() => onSwitchAgent(audioAgent)}
          >
            Use {agentName(audioAgent)}
          </button>
        )}
        <button type="button" disabled={disabled} onClick={onClearAudio}>
          Remove audio
        </button>
      </div>
    </div>
  );
}

/** The draft's screenshots, audio clips, long pastes, and attachment warnings above the input. */
export function ComposerAttachmentStrip({
  draft,
  to,
  onSwitchAgent,
  onOpenPaste,
  onRemovePaste,
}: {
  draft: ComposerDraft;
  to: Recipient;
  onSwitchAgent?: (provider: AgentProvider) => void;
  onOpenPaste: (index: number) => void;
  onRemovePaste: (index: number) => void;
}) {
  const { attached, audio, pastes, incoming, error } = draft;
  const hasCards =
    attached.length > 0 || audio.length > 0 || pastes.length > 0 || incoming > 0;
  const audioUnsupported = audio.length > 0 && !canReceiveAudio(to);
  if (!hasCards && !error) return null;

  return (
    <>
      {hasCards && (
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
              unsupported={audioUnsupported}
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
      )}
      {audioUnsupported && (
        <AudioUnsupportedWarning
          to={to}
          disabled={draft.preparing}
          onSwitchAgent={onSwitchAgent}
          onClearAudio={draft.clearAudio}
        />
      )}
      {error && (
        <p className="composer-image-error" role="alert">
          {error}
        </p>
      )}
    </>
  );
}
