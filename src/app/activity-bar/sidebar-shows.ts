import { persistedStore } from "../../lib/persisted-store";

/** What the sidebar lists: this computer's threads, or the paired computers' own. */
export type SidebarShows = "threads" | "computers";

const shows = persistedStore<SidebarShows>(
  "relay-sidebar-shows",
  (saved) => (saved === "computers" ? "computers" : "threads"),
  (value) => (value === "threads" ? null : value),
);

export const useSidebarShows = shows.use;
export const setSidebarShows = shows.set;
