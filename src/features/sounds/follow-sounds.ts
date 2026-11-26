import type { QueryClient } from "@tanstack/react-query";
import { api } from "../../lib/api";
import type { Project } from "../../../shared/projects";
import { pickSound, SoundCues } from "../../../shared/sound-cues";
import { pickTap } from "../../../shared/trackpad";
import { openThreadId } from "../thread/arrival";
import { focusedThreadWindow } from "../thread-windows/thread-windows";
import { playSound } from "./player";
import { soundSettingsQuery, trackpadSettingsQuery } from "./state";

/**
 * Thread sounds while the main window is open, for its threads and those in
 * windows of their own, which play none themselves. Nothing sounds for the
 * thread you're looking at, here or in its own window.
 */
export function followSounds(qc: QueryClient) {
  const cues = new SoundCues({
    hears: (chatId) =>
      !(document.hasFocus() && openThreadId() === chatId) &&
      focusedThreadWindow() !== chatId,
    async play(heard) {
      // A main process from before these has nothing to answer with.
      const [sounds = {}, trackpad = {}] = await Promise.all([
        qc.query(soundSettingsQuery).catch(() => undefined),
        qc.query(trackpadSettingsQuery).catch(() => undefined),
      ]);
      const projects = qc
        .getQueriesData<Project[]>({ queryKey: ["projects"] })
        .flatMap(([, list]) => list ?? []);
      const projectSounds = (id: string) =>
        projects.find((p) => p.id === id)?.settings?.sounds;
      const tap = pickTap(heard, trackpad, projectSounds);
      if (tap) void api.tapTrackpad(tap.tap, tap.strength).catch(() => {});
      const sound = pickSound(heard, sounds, projectSounds);
      if (sound) await playSound(sound.sound, sound.volume).catch(() => {});
      return !!(tap || sound);
    },
  });
  return api.onThreadNews((item) => cues.news(item));
}
