import type { Entry } from "../agent-host/protocol";

/**
 * Restart replay for a JSON-RPC agent kept in the agent host: what a turn cut
 * off by a restart still has to hear, and where Relay's request ids go on.
 */

const parsed = (text: string): Record<string, unknown> | undefined => {
  try {
    const value = JSON.parse(text);
    return typeof value === "object" && value ? value : undefined;
  } catch {
    return undefined;
  }
};

/** Where Relay's request `id` was answered. */
const answeredAt = (entries: Entry[], id: number) =>
  entries.find((entry) => {
    if (entry.kind !== "line") return false;
    const said = parsed(entry.text);
    return said?.id === id && !("method" in said);
  })?.seq;

/**
 * The lines said since `split`: notifications, replies, and the agent's own
 * requests the last Relay never answered. With `after`, what came up to the
 * answer to that request of Relay's is left out, as shown already.
 */
export function replayLines(
  entries: Entry[],
  split: number,
  after?: number,
): string[] {
  const shownTo = after === undefined ? undefined : answeredAt(entries, after);
  const from = shownTo === undefined ? split : Math.max(split, shownTo + 1);
  const answered = new Set<unknown>();
  for (const entry of entries) {
    if (entry.kind !== "input") continue;
    const sent = parsed(entry.text);
    if (sent && !("method" in sent) && "id" in sent) answered.add(sent.id);
  }
  return entries.flatMap((entry) => {
    if (entry.kind !== "line" || entry.seq < from) return [];
    const said = parsed(entry.text);
    if (said && "method" in said && "id" in said && answered.has(said.id)) return [];
    return [entry.text];
  });
}

/** The highest id Relay gave a request, in what it sent or the agent answered. */
export function highestRequestId(entries: Entry[]) {
  let highest = 0;
  for (const entry of entries) {
    if (entry.kind !== "line" && entry.kind !== "input") continue;
    const message = parsed(entry.text);
    // Relay's requests go in with a method and come back without one; the
    // agent's own are numbered by the agent.
    const relays = entry.kind === "input" ? "method" in (message ?? {}) : !("method" in (message ?? {}));
    if (!message || !relays) continue;
    if (typeof message.id === "number") highest = Math.max(highest, message.id);
  }
  return highest;
}
