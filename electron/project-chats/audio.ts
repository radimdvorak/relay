import { copyFile, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { randomUUID } from "node:crypto";
import { join } from "node:path";
import {
  audioExtension,
  maxAudioBytes,
  type AudioMimeType,
} from "../../shared/audio";
import type {
  ChatAudio,
  ChatMessage,
  ProjectChatSend,
} from "../../shared/projects";

const uuidPattern =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const RIFF = Buffer.from("RIFF");
const RF64 = Buffer.from("RF64");
const WAVE = Buffer.from("WAVE");
const FLAC = Buffer.from("fLaC");
const OGGS = Buffer.from("OggS");
const OPUS_HEAD = Buffer.from("OpusHead");
const FTYP = Buffer.from("ftyp");
const EBML = Buffer.from([0x1a, 0x45, 0xdf, 0xa3]);
const ID3 = Buffer.from("ID3");
const ADIF = Buffer.from("ADIF");

/** Identifies a supported audio format from its leading container or frame bytes. */
export function audioMimeType(
  bytes: Buffer,
  declared?: AudioMimeType,
): AudioMimeType | null {
  if (bytes.length < 4) return null;
  if (
    bytes.length >= 12 &&
    (bytes.subarray(0, 4).equals(RIFF) || bytes.subarray(0, 4).equals(RF64)) &&
    bytes.subarray(8, 12).equals(WAVE)
  )
    return "audio/wav";
  if (bytes.subarray(0, 4).equals(FLAC)) return "audio/flac";
  if (bytes.subarray(0, 4).equals(OGGS)) {
    const isOpus = bytes.subarray(0, 64).includes(OPUS_HEAD);
    if (declared === "audio/opus") return isOpus ? "audio/opus" : null;
    if (declared === "audio/ogg") return "audio/ogg";
    return isOpus ? "audio/opus" : "audio/ogg";
  }
  if (bytes.length >= 12 && bytes.subarray(4, 8).equals(FTYP))
    return "audio/mp4";
  if (bytes.subarray(0, 4).equals(EBML)) return "audio/webm";
  if (bytes.subarray(0, 3).equals(ID3))
    return declared === "audio/aac" ? "audio/aac" : "audio/mpeg";
  if (bytes.subarray(0, 4).equals(ADIF)) return "audio/aac";
  // ADTS AAC frame sync (0xFFF with MPEG layer 00) vs MPEG-1/2/2.5 audio frame sync.
  if (bytes[0] === 0xff && (bytes[1]! & 0xf6) === 0xf0) return "audio/aac";
  if (
    bytes[0] === 0xff &&
    (bytes[1]! & 0xe0) === 0xe0 &&
    (bytes[1]! & 0x18) !== 0x08 &&
    (bytes[1]! & 0x06) !== 0x00
  )
    return "audio/mpeg";
  return null;
}

/** A thread's saved audio attachments under `<dir>/audio/<chatId>/`. */
export class ChatAudioFiles {
  constructor(private dir: string) {}

  path(chatId: string, clip: ChatAudio) {
    if (!uuidPattern.test(clip.id))
      throw new Error("Invalid saved audio identity.");
    return join(
      this.dir,
      "audio",
      chatId,
      `${clip.id}.${audioExtension(clip.mimeType)}`,
    );
  }

  /** A thread's saved audio clip as a base64 data URL. */
  async read(chatId: string, clip: ChatAudio) {
    const bytes = await readFile(this.path(chatId, clip));
    return `data:${clip.mimeType};base64,${bytes.toString("base64")}`;
  }

  async save(
    chatId: string,
    clips: NonNullable<ProjectChatSend["audio"]>,
  ): Promise<ChatAudio[]> {
    const checked = clips.map(
      ({ name, mimeType, durationSec, peaks, dataUrl }) => {
        const bytes = Buffer.from(
          dataUrl.slice(dataUrl.indexOf(",") + 1),
          "base64",
        );
        if (
          audioMimeType(bytes, mimeType) !== mimeType ||
          bytes.length > maxAudioBytes
        )
          throw new Error(
            "Audio file is invalid or exceeds the 20MB limit.",
          );
        return {
          meta: {
            id: randomUUID(),
            name,
            mimeType,
            sizeBytes: bytes.length,
            ...(durationSec !== undefined ? { durationSec } : {}),
            ...(peaks?.length ? { peaks } : {}),
          } as ChatAudio,
          bytes,
        };
      },
    );
    await this.folder(chatId);
    for (const { meta, bytes } of checked)
      await writeFile(this.path(chatId, meta), bytes, {
        flag: "wx",
        mode: 0o600,
      });
    return checked.map(({ meta }) => meta);
  }

  /** A fork's copies of the audio clips its messages carry over. */
  async copy(from: string, to: string, messages: ChatMessage[]) {
    const clips = messages.flatMap((m) => m.audio ?? []);
    if (!clips.length) return;
    await this.folder(to);
    for (const clip of clips)
      await copyFile(this.path(from, clip), this.path(to, clip));
  }

  removeAll(chatId: string) {
    return rm(join(this.dir, "audio", chatId), {
      recursive: true,
      force: true,
    });
  }

  private folder(chatId: string) {
    return mkdir(join(this.dir, "audio", chatId), {
      recursive: true,
      mode: 0o700,
    });
  }
}
