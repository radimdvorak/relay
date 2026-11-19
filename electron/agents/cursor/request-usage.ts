import type { UsageTokens } from "../../../shared/usage";

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

/** What one Cursor request used. */
export interface CursorRequest {
  tokens: UsageTokens;
}

/**
 * What each Cursor request used. Cursor counts output as it streams and
 * reports a request's whole usage once it ends; a turn stopped mid-request
 * never gets that report, so the streamed count stands in for it.
 */
export class CursorRequests {
  private streamedTokens = 0;

  delta(tokens: number) {
    this.streamedTokens += tokens;
  }

  /** The request that just ended, with the usage Cursor reported for it. */
  ended(usage: unknown): CursorRequest {
    const streamed = this.streamedTokens;
    this.streamedTokens = 0;
    return { tokens: cursorTokens(usage) ?? outputOnly(streamed) };
  }

  /** The request the turn ended in the middle of, if it wrote anything. */
  cutOff(): CursorRequest | undefined {
    if (this.streamedTokens) return this.ended(undefined);
  }
}
