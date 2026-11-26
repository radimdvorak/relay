import { expect, it } from "vitest";
import type { SoundCue } from "./sound-cues";
import { soundEvents } from "./sounds";
import { pickTap, tapOptions, tapSteps } from "./trackpad";

const settings = {
  finished: "purr",
  waiting: "knock-knock",
} as const;

const done: SoundCue = { event: "finished", projectId: "a" };
const ask: SoundCue = { event: "waiting", projectId: "b" };
const broke: SoundCue = { event: "failed", projectId: "a" };

it("taps for the most urgent cue that has a pattern", () => {
  expect(pickTap([done, ask], settings, () => undefined)).toEqual({
    tap: "knock-knock",
    strength: 0.3,
  });
  expect(pickTap([done, broke], settings, () => undefined)?.tap).toBe("purr");
  expect(pickTap([broke], settings, () => undefined)).toBeUndefined();
});

it("leaves out a project that keeps the event quiet", () => {
  const quiet = (id: string) =>
    id === "b" ? ({ waiting: "off" } as const) : undefined;
  expect(pickTap([ask], settings, quiet)).toBeUndefined();
  expect(pickTap([done, ask], settings, quiet)?.tap).toBe("purr");
});

it("keeps every pattern within the motor's normal strength and under two seconds", () => {
  for (const { value } of soundEvents.flatMap(tapOptions)) {
    const steps = tapSteps(value);
    expect(steps.length, value).toBeGreaterThan(0);
    for (const { level, hz } of steps) {
      expect(level, value).toBeGreaterThan(0);
      expect(level, value).toBeLessThanOrEqual(1);
      expect(hz, value).toBeGreaterThanOrEqual(40);
    }
    const length = steps.reduce((sum, s) => sum + s.wait, 0);
    expect(length, value).toBeLessThan(2000);
  }
});
