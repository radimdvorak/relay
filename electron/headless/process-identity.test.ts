import { expect, it } from "vitest";
import { recordedAlive, recordProcess } from "./process-identity";

const alive = (pid: number) => pid === process.pid;

it("takes a recorded pid only for the process that wrote it", () => {
  expect(recordedAlive(recordProcess(), alive)).toBe(true);
  // The same number, now some other process: after WSL restarts a distribution, say.
  expect(recordedAlive(`${process.pid} 1`, alive)).toBe(false);
  expect(recordedAlive("999999 1", alive)).toBe(false);
  // Written before starts were recorded: the pid alone decides.
  expect(recordedAlive(`${process.pid}\n`, alive)).toBe(true);
  expect(recordedAlive("", alive)).toBe(false);
});

it.runIf(process.platform === "darwin")(
  "records when a process started on macOS too, so a pid reused after a restart doesn't count",
  () => {
    const [pid, start] = recordProcess().split(" ");
    expect(Number(pid)).toBe(process.pid);
    expect(start).toBeTruthy();
    // The same pid, written by a Relay before the computer restarted.
    expect(recordedAlive(`${process.pid} Mon-Jan-1-00:00:00-2024`, alive)).toBe(
      false,
    );
  },
);
