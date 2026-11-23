import { useEffect, useRef, useState } from "react";

// Only one audio tile plays at a time across the window.
let activePlayer: HTMLAudioElement | null = null;

function createPlayer(handlers: {
  onDuration: (seconds: number) => void;
  onTime: (seconds: number) => void;
  onPlaying: (playing: boolean) => void;
  onEnded: () => void;
  onError: () => void;
}) {
  const player = new Audio();
  player.preload = "metadata";
  player.addEventListener("loadedmetadata", () => {
    if (Number.isFinite(player.duration) && player.duration > 0)
      handlers.onDuration(player.duration);
  });
  player.addEventListener("timeupdate", () =>
    handlers.onTime(player.currentTime),
  );
  player.addEventListener("play", () => handlers.onPlaying(true));
  player.addEventListener("pause", () => handlers.onPlaying(false));
  player.addEventListener("ended", () => {
    if (activePlayer === player) activePlayer = null;
    handlers.onEnded();
  });
  player.addEventListener("error", handlers.onError);
  return player;
}

function disposePlayer(player: HTMLAudioElement | null) {
  if (!player) return;
  player.pause();
  if (activePlayer === player) activePlayer = null;
  player.src = "";
}

/** Manages an `<audio>` element with lazy URL loading, seeking, and single-active playback. */
export function useAudioPlayer({
  src,
  load,
  durationSec,
}: {
  src?: string;
  load?: () => Promise<string>;
  durationSec?: number;
}) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [resolvedSrc, setResolvedSrc] = useState<string | undefined>(src);
  const [loading, setLoading] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [failed, setFailed] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState<number | undefined>(durationSec);

  useEffect(() => setResolvedSrc(src), [src]);
  useEffect(() => {
    if (durationSec !== undefined) setDuration(durationSec);
  }, [durationSec]);
  useEffect(() => () => disposePlayer(audioRef.current), []);

  function attach(url: string) {
    audioRef.current ??= createPlayer({
      onDuration: setDuration,
      onTime: setCurrentTime,
      onPlaying: setPlaying,
      onEnded: () => {
        setPlaying(false);
        setCurrentTime(0);
      },
      onError: () => {
        setPlaying(false);
        setFailed(true);
      },
    });
    if (audioRef.current.src !== url) audioRef.current.src = url;
    return audioRef.current;
  }

  async function ensureUrl() {
    if (resolvedSrc || !load) return resolvedSrc;
    setLoading(true);
    setFailed(false);
    try {
      const url = await load();
      setResolvedSrc(url);
      return url;
    } catch {
      setFailed(true);
      return undefined;
    } finally {
      setLoading(false);
    }
  }

  async function togglePlay() {
    if (loading) return;
    if (playing && audioRef.current) return audioRef.current.pause();
    const url = await ensureUrl();
    if (!url) return;
    setFailed(false);
    await playExclusive(attach(url)).catch(() => {
      setPlaying(false);
      setFailed(true);
    });
  }

  async function seekTo(fraction: number) {
    const url = await ensureUrl();
    if (!url) return;
    const next = seekPlayer(attach(url), fraction, duration);
    if (next !== undefined) setCurrentTime(next);
  }

  return { loading, playing, failed, currentTime, duration, togglePlay, seekTo };
}

function playExclusive(player: HTMLAudioElement) {
  if (activePlayer && activePlayer !== player) activePlayer.pause();
  activePlayer = player;
  return player.play();
}

function seekPlayer(
  player: HTMLAudioElement,
  fraction: number,
  fallbackDuration?: number,
) {
  const total =
    Number.isFinite(player.duration) && player.duration > 0
      ? player.duration
      : fallbackDuration;
  if (!total || total <= 0) return undefined;
  const next = Math.max(0, Math.min(1, fraction)) * total;
  player.currentTime = next;
  return next;
}
