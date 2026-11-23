import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { existsSync } from "node:fs";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { maxAudioBytes, type AudioMimeType } from "../../shared/audio";
import type { ChatMessage } from "../../shared/projects";
import { ChatAudioFiles, audioMimeType } from "./audio";

const wavBytes = Buffer.concat([
  Buffer.from("RIFF"),
  Buffer.alloc(4),
  Buffer.from("WAVEfmt "),
]);

const rf64Bytes = Buffer.concat([
  Buffer.from("RF64"),
  Buffer.alloc(4),
  Buffer.from("WAVEds64"),
]);

const flacBytes = Buffer.from("fLaC\x00\x00\x00\x22");
const oggBytes = Buffer.from("OggS\x00\x02vorbis-header-bytes");
const opusBytes = Buffer.from("OggS\x00\x02\x00\x00OpusHead\x01\x02");

const m4aBytes = Buffer.concat([
  Buffer.from([0x00, 0x00, 0x00, 0x18]),
  Buffer.from("ftypM4A "),
]);

const webmBytes = Buffer.from([0x1a, 0x45, 0xdf, 0xa3, 0x9f, 0x42]);
const id3Bytes = Buffer.from("ID3\x04\x00\x00\x00\x00\x00\x00");
const mp3FrameBytes = Buffer.from([0xff, 0xfb, 0x90, 0x00, 0x00, 0x00]);
const adifBytes = Buffer.from("ADIF\x00\x00\x00\x00");
const adtsBytes = Buffer.from([0xff, 0xf1, 0x50, 0x80, 0x02, 0x1f]);

function dataUrl(mimeType: AudioMimeType, bytes: Buffer) {
  return `data:${mimeType};base64,${bytes.toString("base64")}`;
}

describe("audioMimeType", () => {
  it("identifies supported container and frame signatures", () => {
    expect(audioMimeType(wavBytes)).toBe("audio/wav");
    expect(audioMimeType(rf64Bytes)).toBe("audio/wav");
    expect(audioMimeType(flacBytes)).toBe("audio/flac");
    expect(audioMimeType(oggBytes)).toBe("audio/ogg");
    expect(audioMimeType(opusBytes)).toBe("audio/opus");
    expect(audioMimeType(opusBytes, "audio/ogg")).toBe("audio/ogg");
    expect(audioMimeType(oggBytes, "audio/opus")).toBeNull();
    expect(audioMimeType(m4aBytes)).toBe("audio/mp4");
    expect(audioMimeType(webmBytes)).toBe("audio/webm");
    expect(audioMimeType(id3Bytes)).toBe("audio/mpeg");
    expect(audioMimeType(id3Bytes, "audio/aac")).toBe("audio/aac");
    expect(audioMimeType(mp3FrameBytes)).toBe("audio/mpeg");
    expect(audioMimeType(adifBytes)).toBe("audio/aac");
    expect(audioMimeType(adtsBytes)).toBe("audio/aac");
  });

  it("rejects non-audio, truncated, and non-WAVE RIFF headers", () => {
    expect(audioMimeType(Buffer.from("hi"))).toBeNull();
    expect(audioMimeType(Buffer.from("plain text file"))).toBeNull();
    expect(
      audioMimeType(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a])),
    ).toBeNull();
    expect(
      audioMimeType(
        Buffer.concat([Buffer.from("RIFF"), Buffer.alloc(4), Buffer.from("AVI ")]),
      ),
    ).toBeNull();
  });
});

describe("ChatAudioFiles", () => {
  let root: string;
  let files: ChatAudioFiles;

  beforeEach(async () => {
    root = await mkdtemp(join(tmpdir(), "relay-audio-files-"));
    files = new ChatAudioFiles(root);
  });

  afterEach(() => rm(root, { recursive: true, force: true }));

  it("saves verified audio clips, copies them on fork, and deletes them with the thread", async () => {
    const saved = await files.save("chat-1", [
      {
        name: "note.wav",
        mimeType: "audio/wav",
        durationSec: 3.2,
        peaks: [0.2, 0.8],
        dataUrl: dataUrl("audio/wav", wavBytes),
      },
    ]);
    expect(saved).toHaveLength(1);
    expect(saved[0]).toMatchObject({
      name: "note.wav",
      mimeType: "audio/wav",
      sizeBytes: wavBytes.length,
      durationSec: 3.2,
      peaks: [0.2, 0.8],
    });
    const path = files.path("chat-1", saved[0]!);
    expect(await readFile(path)).toEqual(wavBytes);
    expect(await files.read("chat-1", saved[0]!)).toBe(
      dataUrl("audio/wav", wavBytes),
    );

    const messages: ChatMessage[] = [
      {
        id: "m1",
        role: "user",
        body: "",
        status: "complete",
        created: 1,
        provider: "antigravity",
        version: 1,
        audio: saved,
      },
    ];
    await files.copy("chat-1", "chat-2", messages);
    expect(await readFile(files.path("chat-2", saved[0]!))).toEqual(wavBytes);

    await files.removeAll("chat-1");
    expect(existsSync(path)).toBe(false);
    expect(existsSync(files.path("chat-2", saved[0]!))).toBe(true);
  });

  it("rejects mismatched bytes, oversized clips, and unsafe clip identities", async () => {
    await expect(
      files.save("chat-1", [
        {
          name: "fake.wav",
          mimeType: "audio/wav",
          dataUrl: dataUrl("audio/wav", Buffer.from("not actually a wav")),
        },
      ]),
    ).rejects.toThrow("20MB limit");

    const huge = Buffer.alloc(maxAudioBytes + 1);
    wavBytes.copy(huge);
    await expect(
      files.save("chat-1", [
        {
          name: "huge.wav",
          mimeType: "audio/wav",
          dataUrl: dataUrl("audio/wav", huge),
        },
      ]),
    ).rejects.toThrow("20MB limit");

    expect(() =>
      files.path("chat-1", {
        id: "../escape",
        name: "bad.wav",
        mimeType: "audio/wav",
        sizeBytes: 12,
      }),
    ).toThrow("Invalid saved audio identity.");
  });
});
