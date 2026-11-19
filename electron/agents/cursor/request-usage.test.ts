import { describe, expect, it } from "vitest";
import { CursorRequests } from "./request-usage";

/** Requests on a clock the test moves, fed counts as `[ms, tokens]`. */
function requests() {
  let at = 0;
  const counted = new CursorRequests(() => at);
  const counts = (...deltas: [number, number][]) => {
    for (const [ms, tokens] of deltas) {
      at = ms;
      counted.delta(tokens);
    }
  };
  return { counted, counts };
}

const usage = { inputTokens: 100, outputTokens: 60, cacheReadTokens: 80 };

describe("timing a Cursor request", () => {
  it("times what streamed after the first count, across the stretches between tool calls", () => {
    const { counted, counts } = requests();
    counts([0, 2], [100, 4], [200, 6]);
    counted.paused();
    counts([1000, 2], [1500, 50]);
    expect(counted.ended(usage)).toEqual({
      tokens: { input: 20, cacheRead: 80, cacheWrite: 0, output: 60 },
      streamed: { output: 60, ms: 700 },
    });
  });

  it("leaves out the tokens a tool call lands with once it has run", () => {
    const { counted, counts } = requests();
    counts([0, 2], [100, 4], [200, 6]);
    counted.paused();
    counts([900, 23]);
    expect(counted.ended(usage).streamed).toEqual({ output: 10, ms: 200 });
  });

  it("leaves untimed an answer that arrived in one burst", () => {
    // Claude through Cursor: 451 tokens in 23ms once the answer was written.
    const { counted, counts } = requests();
    counts(
      [0, 5],
      ...Array.from({ length: 23 }, (_, i): [number, number] => [i + 1, 19]),
    );
    expect(counted.ended(usage).streamed).toBeUndefined();
  });

  it("starts each request's clock afresh", () => {
    const { counted, counts } = requests();
    counts([0, 1], [1000, 50]);
    counted.ended(usage);
    counts([5000, 1], [5100, 10]);
    expect(counted.ended(usage).streamed).toEqual({ output: 10, ms: 100 });
  });

  it("counts a request cut off before Cursor reported it from what streamed", () => {
    const { counted, counts } = requests();
    expect(counted.cutOff()).toBeUndefined();
    counts([0, 3], [200, 20]);
    expect(counted.cutOff()).toEqual({
      tokens: { input: 0, cacheRead: 0, cacheWrite: 0, output: 23 },
      streamed: { output: 20, ms: 200 },
    });
  });
});
