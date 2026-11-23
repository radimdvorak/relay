import { useMemo, useState } from "react";
import { agentMentionPattern } from "../../../shared/agents";
import { parseCodeReferences } from "../../../shared/code-references";
import { onlyImageTokens } from "../../../shared/image-refs";
import type { ProjectFileLink } from "../../../shared/project-file-links";
import type { ProjectChatSend } from "../../../shared/projects";
import { AudioTile } from "../audio/AudioTile";
import { BrowserAnnotationChip } from "../images/BrowserAnnotationChip";
import { ImageThumbnail } from "../images/ImagePreview";
import { ImageViewer } from "../images/ImageViewer";
import { SentMemes, useSentMemes } from "../memes/SentMemes";
import { CodeReferenceList } from "./CodeReferenceChip";
import type { SentImage } from "./SentPills";
import { pilledImages, UserText } from "./UserText";

/** A queued screenshot as a sent one: it has no id yet, so it loads from what the queue holds. */
function queuedImages(input: ProjectChatSend): SentImage[] {
  return (input.images ?? []).map((image, i) => ({
    image: {
      id: `${input.id}-${i}`,
      name: image.name,
      mimeType: image.mimeType,
      browser: image.browser,
      sizeBytes: Math.floor(
        ((image.dataUrl.length - image.dataUrl.indexOf(",") - 1) * 3) / 4,
      ),
    },
    preview: {
      key: `queued:${input.id}:${i}`,
      name: image.name,
      load: () => Promise.resolve(image.dataUrl),
    },
  }));
}

function QueuedAudio({
  id,
  audio,
}: {
  id: string;
  audio: ProjectChatSend["audio"];
}) {
  if (!audio?.length) return null;
  return (
    <div className="message-audio">
      {audio.map((clip, i) => (
        <AudioTile
          key={`${id}-audio-${i}`}
          name={clip.name}
          mimeType={clip.mimeType}
          durationSec={clip.durationSec}
          peaks={clip.peaks}
          load={() => Promise.resolve(clip.dataUrl)}
        />
      ))}
    </div>
  );
}

/** A queued message as it will read once sent: its screenshots, audio, code references, memes and pills. */
export function QueuedBody({
  input,
  projectRoot,
  onOpenFile,
}: {
  input: ProjectChatSend;
  projectRoot: string;
  onOpenFile: (target: ProjectFileLink) => void;
}) {
  const [viewing, setViewing] = useState<string>();
  const code = useMemo(() => parseCodeReferences(input.body), [input.body]);
  const said = code.body.replace(agentMentionPattern, "");
  const text = onlyImageTokens(said) ? "" : said;
  const memes = useSentMemes(text);
  const images = useMemo(() => queuedImages(input), [input]);
  const pilled = useMemo(() => pilledImages(text), [text]);
  const strip = images.filter(
    (sent, i) => !pilled.has(i + 1) && !sent.image.browser,
  );
  const previews = images.map((sent) => sent.preview);
  const viewingIndex = previews.findIndex((image) => image.key === viewing);
  return (
    <>
      {images
        .filter((sent) => sent.image.browser)
        .map(({ image, preview }) => (
          <BrowserAnnotationChip
            key={image.id}
            annotation={image.browser!}
            image={preview}
            onOpen={() => setViewing(preview.key)}
          />
        ))}
      {!!strip.length && (
        <div className="message-images">
          {strip.map(({ preview }) => (
            <ImageThumbnail
              key={preview.key}
              image={preview}
              onOpen={() => setViewing(preview.key)}
            />
          ))}
        </div>
      )}
      <QueuedAudio id={input.id} audio={input.audio} />
      {!!code.refs.length && (
        <CodeReferenceList
          references={code.refs}
          onOpen={(ref) =>
            onOpenFile({ path: ref.path, line: ref.start, directory: false })
          }
        />
      )}
      {!!memes.memes.length && <SentMemes memes={memes.memes} />}
      {!!memes.rest?.trim() && (
        <UserText
          text={memes.rest}
          skills={input.skills}
          images={images}
          onOpenImage={setViewing}
          projectRoot={projectRoot}
          onOpenFile={onOpenFile}
        />
      )}
      {viewingIndex >= 0 && (
        <ImageViewer
          images={previews}
          index={viewingIndex}
          onIndex={(index) => setViewing(previews[index].key)}
          onClose={() => setViewing(undefined)}
        />
      )}
    </>
  );
}
