// Who's who among the Claude Code sessions on this computer. Each running
// session lists itself in `<home>/sessions/<pid>.json`, with the name other
// sessions message it by and the socket their messages come in on.
import { readdir, readFile, realpath } from "node:fs/promises";
import { join } from "node:path";

export interface ListedSession {
  sessionId: string;
  name: string;
  socket?: string;
  updatedAt: number;
}

function parsed(text: string): ListedSession | undefined {
  try {
    const entry = JSON.parse(text);
    if (typeof entry?.sessionId !== "string") return undefined;
    if (typeof entry.name !== "string" || !entry.name) return undefined;
    return {
      sessionId: entry.sessionId,
      name: entry.name,
      ...(typeof entry.messagingSocketPath === "string"
        ? { socket: entry.messagingSocketPath }
        : {}),
      updatedAt: Number(entry.updatedAt) || 0,
    };
  } catch {
    return undefined;
  }
}

async function listedIn(dir: string) {
  const names = await readdir(dir).catch(() => [] as string[]);
  const read = await Promise.all(
    names
      .filter((name) => /^\d+\.json$/.test(name))
      .map((name) =>
        readFile(join(dir, name), "utf8").then(parsed, () => undefined),
      ),
  );
  return read.filter((entry): entry is ListedSession => !!entry);
}

/** The sessions listed under any of `homes`; accounts' folders may link to the same one. */
export async function listedSessions(homes: string[]) {
  const dirs = new Set<string>();
  for (const home of homes) {
    const dir = await realpath(join(home, "sessions")).catch(() => undefined);
    if (dir) dirs.add(dir);
  }
  const all = (await Promise.all([...dirs].map(listedIn))).flat();
  const latest = new Map<string, ListedSession>();
  for (const entry of all) {
    const known = latest.get(entry.sessionId);
    if (!known || known.updatedAt < entry.updatedAt)
      latest.set(entry.sessionId, entry);
  }
  return [...latest.values()];
}

/**
 * The session a SendMessage `to` reaches: a name, a name with the `[ref]` a
 * listing gave it, or the `from` address of a message it's answering.
 */
export function addressee(sessions: ListedSession[], to: string) {
  const address = to.trim();
  if (address.startsWith("uds:")) {
    const socket = address.slice("uds:".length);
    return sessions.find((s) => s.socket === socket);
  }
  const [, name = address, ref] =
    /^(.*?)(?:\s+\[([0-9a-f]+)\])?$/i.exec(address) ?? [];
  const named = sessions
    .filter((s) => s.name === name)
    .sort((a, b) => b.updatedAt - a.updatedAt);
  return (ref && named.find((s) => s.sessionId.startsWith(ref))) || named[0];
}
