import {
  addStreamed,
  streamedIfReal,
  type Streamed,
  type UsageTokens,
} from "../../../shared/usage";

/** A request's tokens from Cursor's `usage`, whose input counts the cached part too. */
export function cursorTokens(usage: unknown): UsageTokens | undefined {
  if (!usage || typeof usage !== "object") return;
  const said = usage as Record<string, unknown>;
  if (typeof said.outputTokens !== "number") return;
  const count = (value: unknown) =>
    typeof value === "number" && value > 0 ? value : 0;
  const cacheRead = count(said.cacheReadTokens);
  const cacheWrite = count(said.cacheWriteTokens);
  return {
    input: Math.max(0, count(said.inputTokens) - cacheRead - cacheWrite),
    cacheRead,
    cacheWrite,
    output: count(said.outputTokens),
  };
}

const outputOnly = (output: number): UsageTokens => ({
  input: 0,
  cacheWrite: 0,
  cacheRead: 0,
  output,
});

/** What one Cursor request used, and how fast it wrote when that could be timed. */
export interface CursorRequest {
  tokens: UsageTokens;
  streamed?: Streamed;
}

/**
 * What each Cursor request used. Cursor counts output as it streams and
 * reports a request's whole usage once it ends; a turn stopped mid-request
 * never gets that report, so the streamed count stands in for it.
 *
 * The streamed counts also time the request. They include thinking the model
 * shows, and Claude's answers through Cursor arrive in one burst once
 * written, which `streamedIfReal` leaves untimed. A tool call arrives whole,
 * so the clock stops for it and starts again with the next count.
 */
export class CursorRequests {
  private streamedTokens = 0;
  private stretch?: { from: number; to: number; tokens: number };
  private timed?: Streamed;

  constructor(private now = Date.now) {}

  delta(tokens: number) {
    this.streamedTokens += tokens;
    const at = this.now();
    if (!this.stretch) {
      // This count's tokens were written before the clock could start.
      this.stretch = { from: at, to: at, tokens: 0 };
      return;
    }
    this.stretch.to = at;
    this.stretch.tokens += tokens;
  }

  /** A tool call came out whole: the model isn't streaming until it counts again. */
  paused() {
    const stretch = this.stretch;
    this.stretch = undefined;
    if (!stretch) return;
    const real = streamedIfReal(stretch.tokens, stretch.to - stretch.from);
    if (real) this.timed = addStreamed(this.timed, real);
  }

  /** The request that just ended, with the usage Cursor reported for it. */
  ended(usage: unknown): CursorRequest {
    this.paused();
    const { streamedTokens, timed } = this;
    this.streamedTokens = 0;
    this.timed = undefined;
    return {
      tokens: cursorTokens(usage) ?? outputOnly(streamedTokens),
      ...(timed && { streamed: timed }),
    };
  }

  /** The request the turn ended in the middle of, if it wrote anything. */
  cutOff(): CursorRequest | undefined {
    if (this.streamedTokens) return this.ended(undefined);
  }
}
