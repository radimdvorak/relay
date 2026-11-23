import { useMemo, type KeyboardEvent, type MouseEvent } from "react";
import { Pause, Play, X } from "lucide-react";
import {
  audioFormatLabel,
  formatAudioDuration,
  type AudioMimeType,
} from "../../../shared/audio";
import { Spinner } from "../../ui/ui";
import { useAudioPlayer } from "./useAudioPlayer";
import "./audio-tiles.css";

const BAR_COUNT = 24;

function fallbackPeaks(seed: string): number[] {
  let hash = 2166136261;
  for (let i = 0; i < seed.length; i++)
    hash = Math.imul(hash ^ seed.charCodeAt(i), 16777619);
  return Array.from({ length: BAR_COUNT }, (_, i) => {
    hash = Math.imul(hash ^ (i + 17), 16777619);
    const wave = 0.25 + 0.55 * Math.sin((i / (BAR_COUNT - 1)) * Math.PI);
    const jitter = ((hash >>> 0) % 100) / 100;
    return Math.min(1, Math.max(0.16, wave * (0.55 + jitter * 0.6)));
  });
}

function normalizePeaks(peaks: number[] | undefined, seed: string): number[] {
  if (!peaks?.length) return fallbackPeaks(seed);
  if (peaks.length === BAR_COUNT) return peaks;
  return Array.from({ length: BAR_COUNT }, (_, i) => {
    const idx = Math.min(
      peaks.length - 1,
      Math.floor((i / BAR_COUNT) * peaks.length),
    );
    return peaks[idx] ?? 0.2;
  });
}

function AudioWaveform({
  name,
  bars,
  progress,
  currentTime,
  duration,
  onSeek,
}: {
  name: string;
  bars: number[];
  progress: number;
  currentTime: number;
  duration?: number;
  onSeek: (fraction: number) => void;
}) {
  function onClick(event: MouseEvent<HTMLDivElement>) {
    const rect = event.currentTarget.getBoundingClientRect();
    if (rect.width) onSeek((event.clientX - rect.left) / rect.width);
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (!duration || duration <= 0) return;
    const step = Math.max(1, duration / 10);
    if (event.key === "ArrowRight") {
      event.preventDefault();
      onSeek((currentTime + step) / duration);
    } else if (event.key === "ArrowLeft") {
      event.preventDefault();
      onSeek((currentTime - step) / duration);
    }
  }

  return (
    <div
      className="audio-tile-wave"
      role="slider"
      tabIndex={0}
      aria-label={`Seek ${name}`}
      aria-valuemin={0}
      aria-valuemax={Math.round(duration ?? 0)}
      aria-valuenow={Math.round(currentTime)}
      onClick={onClick}
      onKeyDown={onKeyDown}
    >
      {bars.map((peak, index) => (
        <span
          key={index}
          className="audio-tile-bar"
          data-active={
            (progress > 0 && (index + 0.5) / bars.length <= progress) ||
            undefined
          }
          style={{ height: `${Math.max(18, Math.round(peak * 100))}%` }}
        />
      ))}
    </div>
  );
}

export interface AudioTileProps {
  name: string;
  mimeType: AudioMimeType;
  durationSec?: number;
  peaks?: number[];
  /** Direct source URL (composer draft data URL). */
  src?: string;
  /** Lazy loader for sent clips streamed from relay-media://. */
  load?: () => Promise<string>;
  onRemove?: () => void;
  removeDisabled?: boolean;
  /** Current composer recipient cannot receive audio attachments. */
  unsupported?: boolean;
}

function PlayIcon({ loading, playing }: { loading: boolean; playing: boolean }) {
  if (loading) return <Spinner size={12} />;
  if (playing) return <Pause size={12} fill="currentColor" />;
  return <Play size={12} fill="currentColor" className="audio-tile-play-icon" />;
}

/** Compact 74px waveform card for attached or sent audio clips. */
export function AudioTile({
  name,
  mimeType,
  durationSec,
  peaks,
  src,
  load,
  onRemove,
  removeDisabled,
  unsupported,
}: AudioTileProps) {
  const { loading, playing, failed, currentTime, duration, togglePlay, seekTo } =
    useAudioPlayer({ src, load, durationSec });
  const bars = useMemo(() => normalizePeaks(peaks, name), [peaks, name]);
  const progress =
    duration && duration > 0 ? Math.min(1, currentTime / duration) : 0;
  const timeLabel =
    playing || currentTime > 0
      ? `${formatAudioDuration(currentTime)} / ${formatAudioDuration(duration)}`
      : formatAudioDuration(duration);

  return (
    <div
      className="audio-tile"
      data-playing={playing || undefined}
      data-removable={onRemove ? "true" : undefined}
      data-unsupported={unsupported || undefined}
    >
      <div className="audio-tile-top">
        <button
          type="button"
          className="audio-tile-play"
          aria-label={playing ? `Pause ${name}` : `Play ${name}`}
          onClick={() => void togglePlay()}
          disabled={loading}
        >
          <PlayIcon loading={loading} playing={playing} />
        </button>
        <span className="audio-tile-name" title={name}>
          {name}
        </span>
        <span className="audio-tile-format">{audioFormatLabel(mimeType)}</span>
      </div>
      <div className="audio-tile-bottom">
        <AudioWaveform
          name={name}
          bars={bars}
          progress={progress}
          currentTime={currentTime}
          duration={duration}
          onSeek={(fraction) => void seekTo(fraction)}
        />
        <span
          className="audio-tile-time"
          title={failed ? "Could not play audio" : undefined}
        >
          {failed ? "Error" : timeLabel}
        </span>
      </div>
      {onRemove && (
        <button
          type="button"
          className="composer-image-remove audio-tile-remove"
          disabled={removeDisabled}
          aria-label={`Remove ${name}`}
          onClick={onRemove}
        >
          <X size={13} />
        </button>
      )}
    </div>
  );
}
