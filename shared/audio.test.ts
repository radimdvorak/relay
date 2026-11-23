import { describe, expect, it } from "vitest";
import {
  audioExtension,
  audioFormatLabel,
  audioMimeFromName,
  audioMimeTypes,
  formatAudioDuration,
  normalizeAudioMime,
  pastedAudioSchema,
} from "./audio";

describe("shared/audio", () => {
  it("normalizes browser and OS audio MIME aliases including codec parameters", () => {
    expect(normalizeAudioMime("audio/wav")).toBe("audio/wav");
    expect(normalizeAudioMime("audio/x-wav")).toBe("audio/wav");
    expect(normalizeAudioMime("audio/wave")).toBe("audio/wav");
    expect(normalizeAudioMime("audio/mp3")).toBe("audio/mpeg");
    expect(normalizeAudioMime("audio/x-m4a")).toBe("audio/mp4");
    expect(normalizeAudioMime("audio/x-flac")).toBe("audio/flac");
    expect(normalizeAudioMime("AUDIO/WEBM; codecs=opus")).toBe("audio/webm");
    expect(normalizeAudioMime("audio/ogg;codecs=vorbis")).toBe("audio/ogg");
    expect(normalizeAudioMime("video/webm")).toBeNull();
    expect(normalizeAudioMime("image/png")).toBeNull();
    expect(normalizeAudioMime("")).toBeNull();
    expect(normalizeAudioMime(null)).toBeNull();
  });

  it("infers audio MIME types from filenames case-insensitively", () => {
    expect(audioMimeFromName("clip.wav")).toBe("audio/wav");
    expect(audioMimeFromName("VOICE.M4A")).toBe("audio/mp4");
    expect(audioMimeFromName("demo.track.opus")).toBe("audio/opus");
    expect(audioMimeFromName("notes.txt")).toBeNull();
    expect(audioMimeFromName("no-extension")).toBeNull();
  });

  it("maps every supported audio MIME type to an extension and format label", () => {
    for (const mime of audioMimeTypes) {
      expect(audioExtension(mime)).toMatch(/^[a-z0-9]+$/);
      expect(audioFormatLabel(mime)).toMatch(/^[A-Z0-9]+$/);
    }
  });

  it("formats clip durations consistently for sub-minute, minute, and hour clips", () => {
    expect(formatAudioDuration(undefined)).toBe("0:00");
    expect(formatAudioDuration(Number.NaN)).toBe("0:00");
    expect(formatAudioDuration(-3)).toBe("0:00");
    expect(formatAudioDuration(0)).toBe("0:00");
    expect(formatAudioDuration(4.9)).toBe("0:04");
    expect(formatAudioDuration(65)).toBe("1:05");
    expect(formatAudioDuration(3661)).toBe("1:01:01");
  });

  it("validates pasted audio payloads and rejects non-audio data URLs or invalid peaks", () => {
    const valid = {
      name: "memo.wav",
      mimeType: "audio/wav",
      durationSec: 12.4,
      peaks: [0.1, 0.5, 1],
      dataUrl: "data:audio/wav;base64,UklGRg==",
    };
    expect(pastedAudioSchema.safeParse(valid).success).toBe(true);
    expect(
      pastedAudioSchema.safeParse({
        ...valid,
        dataUrl: "data:video/webm;base64,UklGRg==",
      }).success,
    ).toBe(false);
    expect(
      pastedAudioSchema.safeParse({ ...valid, peaks: [1.5] }).success,
    ).toBe(false);
    expect(
      pastedAudioSchema.safeParse({ ...valid, name: "   " }).success,
    ).toBe(false);
  });
});
