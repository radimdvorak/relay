import {
  useEffect,
  useRef,
  useState,
  type Dispatch,
  type RefObject,
  type SetStateAction,
} from "react";
import { splitDraftKey } from "../../lib/thread-draft";
import {
  loadDraftAudio,
  onDraftAudioChanged,
  onDraftAudioSaved,
  saveDraftAudio,
  type DraftAudio,
} from "../audio/draft-audio";
import { onDraftTaken } from "./drafts";

/** Persisted draft audio clips, including take/restore across sends. */
export function useDraftAudio(
  key: string,
  setError: (error: string) => void,
) {
  const [audio, setAudio] = useState<DraftAudio[]>([]);
  const audioQueue = useRef<Promise<DraftAudio[]>>(Promise.resolve([]));
  const shownKey = useRef(key);
  shownKey.current = key;
  useSyncDraftAudio(key, audioQueue, setAudio, setError);
  const isShown = () => shownKey.current === key;
  const reset = (next: DraftAudio[]) => {
    audioQueue.current = Promise.resolve(next);
    setAudio(next);
  };
  return {
    audio,
    isShown,
    read: () => (isShown() ? audioQueue.current : loadDraftAudio(key)),
    reset,
    async save(next: DraftAudio[]) {
      await saveDraftAudio(key, next);
      if (isShown()) reset(next);
    },
    removeAudio({ id }: DraftAudio) {
      const next = audio.filter((clip) => clip.id !== id);
      setAudio(next);
      audioQueue.current = saveDraftAudio(key, next)
        .then(() => next)
        .catch(() => {
          setError("Could not remove audio from the draft.");
          return next;
        });
    },
    clearAudio() {
      if (!audio.length) return;
      setAudio([]);
      audioQueue.current = saveDraftAudio(key, [])
        .then(() => [])
        .catch(() => {
          setError("Could not remove audio from the draft.");
          return [];
        });
    },
    take: () =>
      takeAudio(audio, {
        queue: audioQueue,
        setAudio,
        edit: (change, failed) =>
          editDraftAudio(
            key,
            shownKey.current,
            audioQueue,
            setAudio,
            change,
            failed,
            setError,
          ),
      }),
  };
}

function useSyncDraftAudio(
  key: string,
  queue: RefObject<Promise<DraftAudio[]>>,
  setAudio: Dispatch<SetStateAction<DraftAudio[]>>,
  setError: (error: string) => void,
) {
  useEffect(() => {
    let live = true;
    const loaded = loadDraftAudio(key);
    queue.current = loaded;
    void loaded
      .then((saved) => live && setAudio(saved))
      .catch(() => live && setError("Could not restore attached audio."));
    return () => {
      live = false;
    };
  }, [key]);
  useEffect(() => {
    let live = true;
    const reload = () => {
      const loaded = loadDraftAudio(key);
      queue.current = loaded;
      void loaded.then((saved) => live && setAudio(saved)).catch(() => {});
    };
    const off = onDraftAudioChanged((changed) => {
      if (changed === key) reload();
    });
    const offSaved = onDraftAudioSaved((changed, saved) => {
      if (changed !== key) return;
      queue.current = Promise.resolve(saved);
      setAudio(saved);
    });
    const offTaken = onDraftTaken((thread) => {
      if (splitDraftKey(key)?.thread === thread) reload();
    });
    return () => {
      live = false;
      off();
      offSaved();
      offTaken();
    };
  }, [key]);
}

function editDraftAudio(
  of: string,
  shown: string,
  queue: RefObject<Promise<DraftAudio[]>>,
  setAudio: Dispatch<SetStateAction<DraftAudio[]>>,
  change: (now: DraftAudio[]) => DraftAudio[],
  failed: string,
  setError: (error: string) => void,
): Promise<unknown> {
  if (shown !== of)
    return loadDraftAudio(of)
      .then((now) => saveDraftAudio(of, change(now)))
      .catch(() => setError(failed));
  const edited = queue.current.then(async (now) => {
    const next = change(now);
    setAudio(next);
    await saveDraftAudio(of, next).catch(() => setError(failed));
    return next;
  });
  queue.current = edited;
  return edited;
}

interface TakenFrom {
  queue: RefObject<Promise<DraftAudio[]>>;
  setAudio: Dispatch<SetStateAction<DraftAudio[]>>;
  edit(
    change: (now: DraftAudio[]) => DraftAudio[],
    failed: string,
  ): Promise<unknown>;
}

function takeAudio(audio: DraftAudio[], from: TakenFrom) {
  let taken: DraftAudio[] | undefined;
  const takenIds = () => new Set(taken?.map((clip) => clip.id));
  return {
    dispatch() {
      taken = audio;
      const ids = takenIds();
      const left = (now: DraftAudio[]) =>
        now.filter((clip) => !ids.has(clip.id));
      from.queue.current = from.queue.current.then(left, () => []);
      from.setAudio(left);
    },
    restore() {
      if (!taken?.length) return;
      const back = taken;
      const ids = takenIds();
      void from.edit(
        (now) => [...back, ...now.filter((clip) => !ids.has(clip.id))],
        "Could not put the audio back in the draft.",
      );
    },
    async forget() {
      if (!taken?.length) return;
      const ids = takenIds();
      await from.edit(
        (now) => now.filter((clip) => !ids.has(clip.id)),
        "Audio was sent, but its draft copy could not be cleared.",
      );
    },
  };
}
