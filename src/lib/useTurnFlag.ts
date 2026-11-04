import { useCallback, useState } from "react";
import { persistedStore } from "./persisted-store";

type Turns = ReturnType<typeof persistedStore<string[]>>;
// One store per key, so a thread's own window follows the main one.
const stores = new Map<string, Turns>();

function turnsUnder(key: string): Turns {
  let store = stores.get(key);
  if (!store) {
    store = persistedStore<string[]>(
      key,
      (saved) => {
        if (saved === null) return [];
        const ids: unknown = JSON.parse(saved);
        return Array.isArray(ids)
          ? ids.filter((id): id is string => typeof id === "string")
          : [];
      },
      (ids) => (ids.length ? JSON.stringify(ids) : null),
    );
    stores.set(key, store);
  }
  return store;
}

/**
 * A yes/no per turn of a thread, kept on this device as the list of turns
 * it's on for under `prefix + chatId`. Without a thread it lasts only as
 * long as the component.
 */
export function useTurnFlag(
  prefix: string,
  chatId: string | undefined,
  messageId: string,
) {
  const store = turnsUnder(prefix + (chatId ?? ""));
  const saved = store.use().includes(messageId);
  const [own, setOwn] = useState(false);
  const setOn = useCallback(
    (on: boolean) => {
      if (!chatId) return setOwn(on);
      const rest = store.get().filter((id) => id !== messageId);
      store.set(on ? [...rest, messageId] : rest);
    },
    [store, chatId, messageId],
  );
  return [chatId ? saved : own, setOn] as const;
}
