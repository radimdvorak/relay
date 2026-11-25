import { execFile, type ChildProcess } from "node:child_process";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { tapSteps, type TapId } from "../../shared/trackpad";

/** Native executables must be outside ASAR, including when their caller is bundled inside it. */
const packagedHelper = () =>
  join(__dirname, "trackpad-taps").replace(
    /app\.asar([\\/])/,
    "app.asar.unpacked$1",
  );

/** Specs run on a real computer, whose trackpad stays still: they name a stand-in to test taps. */
const helperHere = () =>
  process.env.RELAY_TEST_DATA
    ? process.env.RELAY_TEST_TRACKPAD
    : packagedHelper();

/** Longer than any pattern; a motor that hangs doesn't keep a process around. */
const timeoutMs = 5000;

/** Drives the trackpad through its helper; without one it does nothing. */
export class Trackpad {
  private playing: ChildProcess | undefined;

  constructor(private helper: string | undefined = helperHere()) {}

  private get usable() {
    return (
      process.platform === "darwin" && !!this.helper && existsSync(this.helper)
    );
  }

  /** Asked afresh every time: a Magic Trackpad comes and goes. */
  async available() {
    if (!this.usable) return false;
    return new Promise<boolean>((resolve) => {
      execFile(this.helper!, ["--check"], { timeout: timeoutMs }, (error) =>
        resolve(!error),
      );
    });
  }

  /** Plays the pattern, cutting off one still playing. */
  tap(id: TapId, strength: number) {
    if (!this.usable) return;
    this.playing?.kill();
    const steps = tapSteps(id).map((s) =>
      [s.hz, s.ms, s.level.toFixed(3), s.wait].join(":"),
    );
    // A failed tap has nobody to tell: the trackpad may just be unplugged.
    this.playing = execFile(
      this.helper!,
      [String(strength), ...steps],
      { timeout: timeoutMs },
      () => {},
    );
  }
}

/** The one motor, shared by the API and the menubar so a new tap cuts off the last. */
export const trackpad = new Trackpad();
