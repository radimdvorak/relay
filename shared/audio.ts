import { z } from "zod";

export const audioMimeTypes = [
  "audio/wav",
  "audio/mpeg",
  "audio/mp4",
  "audio/ogg",
  "audio/flac",
  "audio/webm",
  "audio/aac",
  "audio/opus",
] as const;

export type AudioMimeType = (typeof audioMimeTypes)[number];

export const audioMimeSchema = z.enum(audioMimeTypes);

/** Largest single audio attachment accepted before base64 encoding. */
export const maxAudioBytes = 20 * 1024 * 1024;

export const maxAudioDataUrlChars = Math.ceil(maxAudioBytes / 3) * 4 + 128;

export const maxAudioAttachments = 10;

const audioDataUrlPattern =
  /^data:audio\/(?:wav|mpeg|mp4|ogg|flac|webm|aac|opus);base64,[A-Za-z0-9+/]+={0,2}$/;

export const pastedAudioSchema = z
  .object({
    name: z.string().trim().min(1).max(120),
    mimeType: audioMimeSchema,
    durationSec: z.number().nonnegative().max(86_400).optional(),
    peaks: z.array(z.number().min(0).max(1)).min(1).max(64).optional(),
    dataUrl: z.string().max(maxAudioDataUrlChars).regex(audioDataUrlPattern),
  })
  .strict();

export type PastedAudio = z.infer<typeof pastedAudioSchema>;

const mimeAliases: Record<string, AudioMimeType> = {
  "audio/wav": "audio/wav",
  "audio/x-wav": "audio/wav",
  "audio/wave": "audio/wav",
  "audio/vnd.wave": "audio/wav",
  "audio/mpeg": "audio/mpeg",
  "audio/mp3": "audio/mpeg",
  "audio/x-mpeg": "audio/mpeg",
  "audio/mp4": "audio/mp4",
  "audio/m4a": "audio/mp4",
  "audio/x-m4a": "audio/mp4",
  "audio/ogg": "audio/ogg",
  "audio/x-ogg": "audio/ogg",
  "audio/flac": "audio/flac",
  "audio/x-flac": "audio/flac",
  "audio/webm": "audio/webm",
  "audio/aac": "audio/aac",
  "audio/x-aac": "audio/aac",
  "audio/aacp": "audio/aac",
  "audio/opus": "audio/opus",
};

const mimeByExtension: Record<string, AudioMimeType> = {
  wav: "audio/wav",
  mp3: "audio/mpeg",
  m4a: "audio/mp4",
  ogg: "audio/ogg",
  flac: "audio/flac",
  webm: "audio/webm",
  aac: "audio/aac",
  opus: "audio/opus",
};

const extensionByMime: Record<AudioMimeType, string> = {
  "audio/wav": "wav",
  "audio/mpeg": "mp3",
  "audio/mp4": "m4a",
  "audio/ogg": "ogg",
  "audio/flac": "flac",
  "audio/webm": "webm",
  "audio/aac": "aac",
  "audio/opus": "opus",
};

const labelByMime: Record<AudioMimeType, string> = {
  "audio/wav": "WAV",
  "audio/mpeg": "MP3",
  "audio/mp4": "M4A",
  "audio/ogg": "OGG",
  "audio/flac": "FLAC",
  "audio/webm": "WEBM",
  "audio/aac": "AAC",
  "audio/opus": "OPUS",
};

/** Maps browser or OS audio MIME variants (including codec parameters) to Relay's canonical set. */
export function normalizeAudioMime(
  mime: string | null | undefined,
): AudioMimeType | null {
  if (!mime) return null;
  const base = mime.split(";")[0]?.trim().toLowerCase() ?? "";
  return mimeAliases[base] ?? null;
}

/** Infers an audio MIME type from a filename's extension. */
export function audioMimeFromName(name: string): AudioMimeType | null {
  const dot = name.lastIndexOf(".");
  if (dot < 0) return null;
  return mimeByExtension[name.slice(dot + 1).toLowerCase()] ?? null;
}

export const audioExtension = (mimeType: AudioMimeType) =>
  extensionByMime[mimeType];

export const audioFormatLabel = (mimeType: AudioMimeType) =>
  labelByMime[mimeType];

/** Formats seconds as `m:ss` (or `h:mm:ss` at an hour and up). */
export function formatAudioDuration(seconds: number | undefined): string {
  if (seconds === undefined || !Number.isFinite(seconds) || seconds < 0)
    return "0:00";
  const total = Math.floor(seconds);
  const hours = Math.floor(total / 3600);
  const mins = Math.floor((total % 3600) / 60);
  const secs = total % 60;
  const secStr = String(secs).padStart(2, "0");
  if (hours > 0) return `${hours}:${String(mins).padStart(2, "0")}:${secStr}`;
  return `${mins}:${secStr}`;
}
