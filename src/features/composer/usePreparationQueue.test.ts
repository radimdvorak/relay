// @vitest-environment happy-dom
import { describe, expect, it, vi } from "vitest";
import { flush, mount, update } from "../../../tests/unit/hook-harness";
import { afterPrevious, usePreparationQueue } from "./usePreparationQueue";

describe("afterPrevious", () => {
  it("runs the next attachment after one that failed, and reports the failure", async () => {
    const failures: unknown[] = [];
    let pending = 0;
    const queue = (chain: Promise<void>, attach: () => Promise<void>) => {
      pending++;
      return afterPrevious(chain, attach, {
        fail: (error) => failures.push(error),
        done: () => pending--,
      });
    };
    const second = vi.fn(async () => {});
    const broken = new Error("disk full");
    let chain = Promise.resolve();
    chain = queue(chain, () => Promise.reject(broken));
    chain = queue(chain, second);
    await chain;
    expect(second).toHaveBeenCalledTimes(1);
    expect(failures).toEqual([broken]);
    expect(pending).toBe(0);
  });
});

it("settles only after batches queued while it waited", async () => {
  const queue = mount(() => usePreparationQueue(() => {}));
  const finish: (() => void)[] = [];
  const attach = () => new Promise<void>((resolve) => finish.push(resolve));
  update(() => queue().queuePreparation(attach, 1));
  let settled = false;
  void queue()
    .settled()
    .then(() => (settled = true));
  update(() => queue().queuePreparation(attach, 1));
  await flush();
  finish[0]!();
  await flush();
  expect(settled).toBe(false);
  finish[1]!();
  await flush();
  expect(settled).toBe(true);
});
