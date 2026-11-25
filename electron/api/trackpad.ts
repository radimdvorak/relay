import { z } from "zod";
import { tapIdSchema, trackpadSettingsSchema } from "../../shared/trackpad";
import { trackpad } from "../trackpad";
import { takes, type ApiContext, type Handlers } from "./context";

const strengthSchema = z.number().min(0).max(1);

/** Settings → Sounds → Trackpad, and the taps the window asks for. */
export function trackpadHandlers({ store }: ApiContext) {
  return {
    trackpadAvailable: () => trackpad.available(),
    trackpadSettings: () => store.get().trackpad ?? {},
    saveTrackpadSettings: takes([trackpadSettingsSchema], async (settings) => {
      await store.update((s) => {
        s.trackpad = settings;
      });
      return settings;
    }),
    tapTrackpad: takes([tapIdSchema, strengthSchema], (tap, strength) =>
      trackpad.tap(tap, strength),
    ),
  } satisfies Handlers;
}
