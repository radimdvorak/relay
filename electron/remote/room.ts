export const maxConnections = 24;
/** A phone has one in the handshake, two while a dead one times out. */
export const handshakesPerAddress = 4;

interface Place {
  from: string;
  deviceId?: string;
  authenticating?: boolean;
  socket: { terminate(): void };
}

/**
 * Makes room for a socket from `from`; false when there's none. Sockets still
 * in the handshake give way to newer ones, oldest first: a phone gets through
 * in milliseconds, so idle ones someone else holds open can't lock it out.
 * Signed-in sockets, and those signing in under the pinned key, stay.
 */
export function makeRoom<T extends Place>(connections: Set<T>, from: string) {
  const waiting = [...connections].filter(
    (c) => !c.deviceId && !c.authenticating,
  );
  const fromHere = waiting.filter((c) => c.from === from);
  const evict =
    fromHere.length >= handshakesPerAddress
      ? fromHere[0]
      : connections.size >= maxConnections
        ? waiting[0]
        : undefined;
  if (evict) {
    connections.delete(evict);
    evict.socket.terminate();
  }
  return connections.size < maxConnections;
}
