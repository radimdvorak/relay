import { useId } from "react";
import { ChevronRight, Images } from "lucide-react";
import { IMAGE_STRIPS } from "../../lib/thread-storage";
import { useTurnFlag } from "../../lib/useTurnFlag";
import { ImageThumbnail, type PreviewImage } from "./ImagePreview";

/** Images inspected during an answer, folded by default and remembered per answer. */
export function AgentImageStrip({
  chatId,
  messageId,
  images,
  onOpen,
}: {
  chatId: string;
  messageId: string;
  images: PreviewImage[];
  onOpen: (image: PreviewImage) => void;
}) {
  const [expanded, setExpanded] = useTurnFlag(IMAGE_STRIPS, chatId, messageId);
  const id = useId();
  return (
    <div className="agent-image-strip">
      <button
        type="button"
        className="agent-image-toggle"
        aria-expanded={expanded}
        aria-controls={id}
        onClick={() => setExpanded(!expanded)}
      >
        <ChevronRight size={13} className="agent-image-chevron" />
        <Images size={14} />
        <span>
          Agent looked at {images.length}{" "}
          {images.length === 1 ? "image" : "images"}
        </span>
      </button>
      <div id={id} hidden={!expanded}>
        {expanded && (
          <div className="message-images">
            {images.map((image) => (
              <ImageThumbnail
                key={image.key}
                image={image}
                onOpen={() => onOpen(image)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
