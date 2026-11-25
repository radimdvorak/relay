// What the trackpad's haptic motor does when a thread finishes, needs you or
// fails. Patterns are data, so tuning one needs no new native helper.
import { z } from "zod";
import type { SoundCue } from "./sound-cues";
import { soundEvents, type ProjectSounds, type SoundEvent } from "./sounds";

/** One vibration, and how long until the next one starts. */
export interface TapStep {
  hz: number;
  ms: number;
  /** 0 to 1, within the strength the user picked. */
  level: number;
  wait: number;
}

const step = (
  hz: number,
  ms: number,
  level: number,
  wait: number,
): TapStep => ({
  hz,
  ms,
  level,
  wait,
});

/** A knuckle on wood: a hard low hit, then the board settling. */
const knock = (level: number, wait: number) => [
  step(150, 16, level, 20),
  step(95, 14, level * 0.45, wait),
];

const heartbeat = (wait: number) => [
  step(80, 34, 0.8, 130),
  step(60, 46, 1, wait),
];

/** Fingers drumming from the little one in. */
const drum = (wait: number) => [
  step(190, 9, 0.5, 62),
  step(175, 9, 0.6, 62),
  step(160, 9, 0.75, 62),
  step(130, 14, 1, wait),
];

/** A cat breathing: soft pulses that swell and fade. */
const purr = Array.from({ length: 30 }, (_, i) =>
  step(72, 24, 0.15 + 0.6 * Math.sin((i / 29) * Math.PI), 40),
);

/** A ball let go of: hits that come quicker and softer. */
const bounce = [1, 0.75, 0.56, 0.42, 0.32, 0.24, 0.18].map((left) =>
  step(140, 14, left, Math.round(260 * left)),
);

/** Each event's patterns, in the order the picker lists them. */
export const tapPatterns = {
  finished: {
    purr: { label: "Purr", steps: purr },
    bounce: { label: "Bounce", steps: bounce },
    "ta-da": {
      label: "Ta-da",
      steps: [step(130, 18, 0.6, 110), step(196, 130, 1, 130)],
    },
    chirp: {
      label: "Chirp",
      steps: [
        step(784, 55, 0.6, 75),
        step(988, 55, 0.7, 75),
        step(1175, 110, 0.8, 110),
      ],
    },
    "one-tap": { label: "One tap", steps: knock(1, 0) },
  },
  waiting: {
    "knock-knock": {
      label: "Knock knock",
      steps: [...knock(0.85, 190), ...knock(1, 0)],
    },
    "three-knocks": {
      label: "Three knocks",
      steps: [...knock(0.8, 150), ...knock(0.9, 150), ...knock(1, 0)],
    },
    heartbeat: {
      label: "Heartbeat",
      steps: [...heartbeat(480), ...heartbeat(0)],
    },
    "drumming-fingers": {
      label: "Drumming fingers",
      steps: [...drum(300), ...drum(0)],
    },
    "shave-and-a-haircut": {
      label: "Shave and a haircut",
      steps: [300, 150, 150, 300, 560, 280, 0].flatMap((wait) =>
        knock(1, Math.max(0, wait - 20)),
      ),
    },
  },
  failed: {
    "sad-trombone": {
      label: "Sad trombone",
      steps: [
        step(311, 150, 0.7, 190),
        step(294, 150, 0.7, 190),
        step(277, 150, 0.7, 190),
        step(262, 420, 0.9, 420),
      ],
    },
    thud: {
      label: "Thud",
      steps: [step(70, 60, 1, 70), step(50, 90, 0.5, 90)],
    },
    "wrong-answer": {
      label: "Wrong answer",
      steps: [step(98, 110, 0.9, 190), step(98, 260, 0.9, 260)],
    },
  },
} as const satisfies Record<
  SoundEvent,
  Record<string, { label: string; steps: readonly TapStep[] }>
>;

export type TapId = {
  [E in SoundEvent]: keyof (typeof tapPatterns)[E];
}[SoundEvent];

const patternsById = new Map<
  string,
  { label: string; steps: readonly TapStep[] }
>(soundEvents.flatMap((event) => Object.entries(tapPatterns[event])));

export const tapIdSchema = z.custom<TapId>(
  (id) => typeof id === "string" && patternsById.has(id),
);

export const tapSteps = (id: TapId) => patternsById.get(id)?.steps ?? [];

export const tapOptions = (event: SoundEvent) =>
  Object.entries(tapPatterns[event]).map(([id, { label }]) => ({
    value: id as TapId,
    label,
  }));

/** An event left unset does nothing, which is how Relay starts. */
export const trackpadSettingsSchema = z
  .object({
    finished: tapIdSchema.optional(),
    waiting: tapIdSchema.optional(),
    failed: tapIdSchema.optional(),
    /** Unset is `defaultTapStrength`. */
    strength: z.number().min(0.05).max(1).optional(),
  })
  .strict();

export type TrackpadSettings = z.infer<typeof trackpadSettingsSchema>;
export const defaultTapStrength = 0.3;

const urgentFirst = ["waiting", "failed", "finished"] as const;

/** The pattern for the most urgent cue that has one. A project that keeps an event quiet keeps the trackpad still too. */
export function pickTap(
  cues: readonly SoundCue[],
  settings: TrackpadSettings,
  projectSounds: (projectId: string) => ProjectSounds | undefined,
): { tap: TapId; strength: number } | undefined {
  for (const event of urgentFirst) {
    const tap = settings[event];
    const wanted = cues.some(
      (cue) =>
        cue.event === event && projectSounds(cue.projectId)?.[event] !== "off",
    );
    if (tap && wanted)
      return { tap, strength: settings.strength ?? defaultTapStrength };
  }
  return undefined;
}

export interface TrackpadApi {
  /** Whether this computer has a trackpad with a haptic motor Relay can drive. */
  trackpadAvailable(): Promise<boolean>;
  trackpadSettings(): Promise<TrackpadSettings>;
  saveTrackpadSettings(settings: TrackpadSettings): Promise<TrackpadSettings>;
  tapTrackpad(tap: TapId, strength: number): Promise<void>;
}
