import type { Store } from "../app/store";

/** Whether every thread's agent may start, message, stop and settle other threads without asking. */
export class ThreadDriving {
  private listeners = new Set<(on: boolean) => void>();

  constructor(private store: Store) {}

  allowed() {
    return this.store.get().drivesThreads === true;
  }

  async allow(on: boolean) {
    await this.store.update((s) => {
      if (on) s.drivesThreads = true;
      else delete s.drivesThreads;
    });
    for (const listener of this.listeners) listener(on);
  }

  /** Hears it turned on or off, here or by an Always; returns the way to stop listening. */
  onChange(listener: (on: boolean) => void) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
}
