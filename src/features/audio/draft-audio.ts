import {
  audioMimeFromName,
  maxAudioBytes,
  normalizeAudioMime,
  type AudioMimeType,
} from "../../../shared/audio";

import type { ProjectChatSend } from "../../../shared/projects";

export type DraftAudio = {
  id: string;
  name: string;
  mimeType: AudioMimeType;
  sizeBytes: number;
  durationSec?: number;
  peaks?: number[];
  dataUrl: string;
};

const DB_NAME = "relay-draft-audio";
const STORE = "drafts";
const PEAK_BARS = 24;

function requested<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.addEventListener("success", () => resolve(request.result));
    request.addEventListener("error", () => reject(request.error));
  });
}

function committed(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    const failed = () =>
      reject(
        transaction.error ?? new Error("Audio storage transaction aborted."),
      );
    transaction.addEventListener("complete", () => resolve());
    transaction.addEventListener("error", failed);
    transaction.addEventListener("abort", failed);
  });
}

async function withDrafts<T>(
  mode: IDBTransactionMode,
  work: (store: IDBObjectStore) => Promise<T> | T,
): Promise<T> {
  if (typeof indexedDB === "undefined") return workFallback<T>(mode);
  const opening = indexedDB.open(DB_NAME, 1);
  opening.addEventListener("upgradeneeded", () => {
    const db = opening.result;
    if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
  });
  const db = await requested(opening);
  db.addEventListener("versionchange", () => db.close());
  try {
    const transaction = db.transaction([STORE], mode);
    const finished = committed(transaction);
    void finished.catch(() => {});
    try {
      const result = await work(transaction.objectStore(STORE));
      await finished;
      return result;
    } catch (error) {
      try {
        transaction.abort();
      } catch {
        /* Already settled. */
      }
      await finished.catch(() => {});
      throw error;
    }
  } finally {
    db.close();
  }
}

function workFallback<T>(mode: IDBTransactionMode): T {
  return (mode === "readonly" ? [] : undefined) as T;
}

const counts = new Map<string, number>();
const written = new Set<string>();
const countListeners = new Set<(key: string) => void>();
const savedListeners = new Set<(key: string, audio: DraftAudio[]) => void>();
let countsRead: Promise<void> | undefined;
let channel: BroadcastChannel | undefined;

function setCount(key: string, count: number) {
  if ((counts.get(key) ?? 0) === count) return;
  if (count > 0) counts.set(key, count);
  else counts.delete(key);
  countListeners.forEach((listener) => listener(key));
}

function changes() {
  if (channel || typeof BroadcastChannel === "undefined") return channel;
  channel = new BroadcastChannel(DB_NAME);
  channel.addEventListener("message", (event: MessageEvent) => {
    if (typeof event.data !== "string") return;
    const key = event.data;
    written.add(key);
    void loadDraftAudio(key)
      .then((saved) => setCount(key, saved.length))
      .catch(() => {});
  });
  return channel;
}

export function onDraftAudioChanged(listener: (key: string) => void) {
  const heard = (event: MessageEvent) => {
    if (typeof event.data === "string") listener(event.data);
  };
  changes()?.addEventListener("message", heard);
  return () => changes()?.removeEventListener("message", heard);
}

export function onDraftAudioSaved(
  listener: (key: string, audio: DraftAudio[]) => void,
) {
  savedListeners.add(listener);
  return () => void savedListeners.delete(listener);
}

/** How many audio clips the draft holds, as far as is known yet. */
export const draftAudioCount = (key: string) => counts.get(key) ?? 0;

/** The draft keys known to hold at least one audio clip. */
export const draftsWithAudio = () => [...counts.keys()];

/** Told the draft key whose audio clip count changed. */
export function onDraftAudioCount(notify: (key: string) => void) {
  countListeners.add(notify);
  changes();
  return () => void countListeners.delete(notify);
}

/** Reads per-draft audio counts once on startup for Activity and draft sync. */
export function readDraftAudioCounts(): Promise<void> {
  changes();
  return (countsRead ??= withDrafts("readonly", async (store) => {
    const [keys, values] = await Promise.all([
      requested(store.getAllKeys()),
      requested(store.getAll() as IDBRequest<DraftAudio[][]>),
    ]);
    keys.forEach((key, i) => {
      if (!written.has(String(key)))
        setCount(String(key), values[i]?.length ?? 0);
    });
  }).catch(() => {
    countsRead = undefined;
  }));
}

export const loadDraftAudio = (key: string): Promise<DraftAudio[]> =>
  withDrafts("readonly", (store) =>
    requested(store.get(key)).then((saved) => saved ?? []),
  );

export async function saveDraftAudio(
  key: string,
  audio: DraftAudio[],
): Promise<void> {
  written.add(key);
  await withDrafts("readwrite", (store) => {
    if (audio.length) store.put(audio, key);
    else store.delete(key);
  });
  setCount(key, audio.length);
  changes()?.postMessage(key);
  savedListeners.forEach((listener) => listener(key, audio));
}

/** Strips local draft bookkeeping before sending audio clips over IPC. */
export function audioSendItems(
  clips: DraftAudio[],
): NonNullable<ProjectChatSend["audio"]> {
  return clips.map(({ name, mimeType, durationSec, peaks, dataUrl }) => ({
    name,
    mimeType,
    ...(durationSec !== undefined ? { durationSec } : {}),
    ...(peaks?.length ? { peaks } : {}),
    dataUrl,
  }));
}

/** Resolves a file's canonical audio MIME type, avoiding video containers like `video/webm`. */
export function resolveAudioMime(file: File): AudioMimeType | null {
  const fromType = normalizeAudioMime(file.type);
  if (fromType) return fromType;
  if (file.type && file.type !== "application/octet-stream") return null;
  return audioMimeFromName(file.name);
}

export const isAudioFile = (file: File) => resolveAudioMime(file) !== null;

function readBase64DataUrl(file: Blob, mimeType: AudioMimeType): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const raw = String(reader.result ?? "");
      const comma = raw.indexOf(",");
      if (comma < 0) return reject(new Error("Could not read audio file."));
      resolve(`data:${mimeType};base64,${raw.slice(comma + 1)}`);
    };
    reader.onerror = () => reject(new Error("Could not read audio file."));
    reader.readAsDataURL(file);
  });
}

function samplePeaks(buffer: AudioBuffer, bars = PEAK_BARS): number[] {
  const channelData = buffer.getChannelData(0);
  if (!channelData.length) return [];
  const step = Math.max(1, Math.floor(channelData.length / bars));
  const raw: number[] = [];
  let max = 0;
  for (let i = 0; i < bars; i++) {
    const start = i * step;
    const end = Math.min(channelData.length, start + step);
    let sum = 0;
    for (let j = start; j < end; j++) {
      const sample = channelData[j]!;
      sum += sample * sample;
    }
    const rms = Math.sqrt(sum / Math.max(1, end - start));
    raw.push(rms);
    if (rms > max) max = rms;
  }
  if (max <= 0) return raw.map(() => 0.15);
  return raw.map((v) => Math.round((v / max) * 100) / 100);
}

async function analyzeAudio(
  file: File,
): Promise<{ durationSec?: number; peaks?: number[] }> {
  const AudioCtx =
    typeof window !== "undefined" ? window.AudioContext : undefined;
  if (!AudioCtx) return {};
  const ctx = new AudioCtx();
  try {
    const bytes = await file.arrayBuffer();
    const decoded = await ctx.decodeAudioData(bytes.slice(0));
    const durationSec = Number.isFinite(decoded.duration)
      ? Math.round(decoded.duration * 10) / 10
      : undefined;
    const peaks = samplePeaks(decoded);
    return {
      ...(durationSec !== undefined ? { durationSec } : {}),
      ...(peaks.length ? { peaks } : {}),
    };
  } catch {
    return {};
  } finally {
    await ctx.close().catch(() => {});
  }
}

export async function prepareAudioFile(file: File): Promise<DraftAudio> {
  const mimeType = resolveAudioMime(file);
  if (!mimeType)
    throw new Error(
      "Attach a WAV, MP3, M4A, OGG, FLAC, WebM, AAC, or Opus audio file.",
    );
  if (!file.size) throw new Error("Audio file is empty.");
  if (file.size > maxAudioBytes)
    throw new Error("Audio file exceeds the 20MB limit.");
  const [dataUrl, meta] = await Promise.all([
    readBase64DataUrl(file, mimeType),
    analyzeAudio(file),
  ]);
  return {
    id: crypto.randomUUID(),
    name: file.name.trim().slice(0, 120) || "Audio clip",
    mimeType,
    sizeBytes: file.size,
    ...meta,
    dataUrl,
  };
}
