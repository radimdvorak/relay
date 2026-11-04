// Opens a thread by its ids from anywhere in the window, for a thread that
// may be new to it, in a project that may be too: the shell, which owns
// navigation, looks them up again first.
const EVENT = "relay:open-thread";

interface ThreadIds {
  projectId: string;
  chatId: string;
}

export function openThreadByIds(thread: ThreadIds) {
  window.dispatchEvent(new CustomEvent<ThreadIds>(EVENT, { detail: thread }));
}

export function onOpenThreadByIds(listener: (thread: ThreadIds) => void) {
  const handle = (e: Event) => listener((e as CustomEvent<ThreadIds>).detail);
  window.addEventListener(EVENT, handle);
  return () => window.removeEventListener(EVENT, handle);
}
