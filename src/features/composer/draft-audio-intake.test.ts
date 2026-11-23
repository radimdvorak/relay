// @vitest-environment happy-dom
import { beforeEach, expect, it, vi } from "vitest";
import { flush, mount, update } from "../../../tests/unit/hook-harness";
import type { DraftAudio } from "../audio/draft-audio";

const savedAudio = new Map<string, DraftAudio[]>();
const audioListeners = new Set<(key: string, audio: DraftAudio[]) => void>();

vi.mock("../../lib/api", () => ({
  api: {
    pathForFile: (file: { path?: string }) => file.path ?? "",
    savePaste: vi.fn(async () => "/data/pastes/pasted-text.txt"),
  },
}));

vi.mock("../images/image-pills", () => ({ useImagePills: () => true }));

vi.mock("../images/draft-images", () => ({
  isScreenshot: () => false,
  loadDraftImages: async () => [],
  saveDraftImages: async () => {},
  onDraftImagesChanged: () => () => {},
  onDraftImagesSaved: () => () => {},
  onDraftImageCount: () => () => {},
  draftImageCount: () => 0,
  draftsWithImages: () => [],
  readDraftImageCounts: async () => {},
  prepareScreenshot: vi.fn(),
}));

vi.mock("../audio/draft-audio", () => ({
  isAudioFile: (file: File) => file.name.endsWith(".wav"),
  loadDraftAudio: async (key: string) => savedAudio.get(key) ?? [],
  saveDraftAudio: async (key: string, audio: DraftAudio[]) => {
    if (audio.length) savedAudio.set(key, audio);
    else savedAudio.delete(key);
    audioListeners.forEach((notify) => notify(key, audio));
  },
  onDraftAudioChanged: () => () => {},
  onDraftAudioSaved: (notify: (key: string, audio: DraftAudio[]) => void) => {
    audioListeners.add(notify);
    return () => audioListeners.delete(notify);
  },
  prepareAudioFile: async (file: File): Promise<DraftAudio> => ({
    id: `id-${file.name}`,
    name: file.name,
    mimeType: "audio/wav",
    sizeBytes: 128,
    durationSec: 4.5,
    dataUrl: "data:audio/wav;base64,UklGRg==",
  }),
}));

beforeEach(() => {
  localStorage.clear();
  savedAudio.clear();
  audioListeners.clear();
});

const audioFile = (name: string, path: string) =>
  Object.assign(new File(["RIFF"], name, { type: "audio/wav" }), { path });

it("attaches dropped audio files when the active agent supports audio", async () => {
  const { useComposerDraft } = await import("./useComposerDraft");
  const insertFiles = vi.fn();
  const draft = mount(() =>
    useComposerDraft(
      "chat-draft:agy",
      { current: { insertFiles, insertImages() {} } as never },
      "antigravity",
    ),
  );
  await flush();

  update(() =>
    draft().addFiles([audioFile("voice.wav", "/tmp/voice.wav")]),
  );
  await flush();

  expect(draft().audio.map((clip) => clip.name)).toEqual(["voice.wav"]);
  expect(draft().hasAttachments).toBe(true);
  expect(insertFiles).not.toHaveBeenCalled();

  update(() => draft().removeAudio(draft().audio[0]!));
  await flush();
  expect(draft().audio).toEqual([]);
  expect(draft().hasAttachments).toBe(false);
});

it("keeps dropped audio files as plain file paths when the active agent lacks audio support", async () => {
  const { useComposerDraft } = await import("./useComposerDraft");
  const insertFiles = vi.fn();
  const draft = mount(() =>
    useComposerDraft(
      "chat-draft:claude",
      { current: { insertFiles, insertImages() {} } as never },
      "claude",
    ),
  );
  await flush();

  update(() =>
    draft().addFiles([audioFile("voice.wav", "/tmp/voice.wav")]),
  );
  await flush();

  expect(draft().audio).toEqual([]);
  expect(insertFiles).toHaveBeenCalledWith(["/tmp/voice.wav"], undefined);
});

it("clears all attached audio clips cleanly via clearAudio", async () => {
  const { useComposerDraft } = await import("./useComposerDraft");
  const draft = mount(() =>
    useComposerDraft(
      "chat-draft:clear",
      { current: { insertFiles() {}, insertImages() {} } as never },
      "antigravity",
    ),
  );
  await flush();

  update(() =>
    draft().addFiles([
      audioFile("one.wav", "/tmp/one.wav"),
      audioFile("two.wav", "/tmp/two.wav"),
    ]),
  );
  await flush();
  expect(draft().audio).toHaveLength(2);

  update(() => draft().clearAudio());
  await flush();
  expect(draft().audio).toEqual([]);
  expect(savedAudio.has("chat-draft:clear")).toBe(false);
});
