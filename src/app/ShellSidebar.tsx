import type { ReactNode } from "react";
import { ScheduleList } from "../features/schedules/ScheduleList";
import { ComputersSidebar } from "../features/computer-threads/ComputersSidebar";
import { useSidebarMin } from "../lib/activity-bar-position";
import { SIDEBAR_WIDTH } from "../lib/settings-page";
import { PaneResizer } from "../ui/PaneResizer";
import { ErrorBox } from "../ui/ui";
import type { Surface } from "./useShellNavigation";
import type { SidebarVisibility } from "./useSidebarVisibility";
import { useSidebarShows } from "./activity-bar/sidebar-shows";

/**
 * The resizable column right of the activity bar. Folded, it floats over
 * the page and peeks out while the pointer is at the window's edge.
 */
export function ShellSidebar({
  sidebar,
  page,
  error,
  children,
}: {
  sidebar: SidebarVisibility;
  /** What the main pane shows. Settings' own nav stands in this column's place. */
  page: Surface | "settings";
  error: unknown;
  /** The threads. */
  children: ReactNode;
}) {
  const away = sidebar.hidden && !sidebar.peek;
  const sidebarMin = useSidebarMin();
  const shows = useSidebarShows();
  // Schedules lists them here, beside the one it shows.
  const pageList = page === "schedules" && <ScheduleList />;
  const computers = page === "project" && shows === "computers" && (
    <ComputersSidebar />
  );
  return (
    <aside
      ref={sidebar.asideRef}
      className={`projects-sidebar ${sidebar.hidden ? "overlay" : ""} ${sidebar.peek ? "peek" : ""}`}
      aria-label="Projects"
      aria-hidden={away || undefined}
      inert={away || undefined}
      hidden={page === "settings"}
      onMouseEnter={(e) => {
        // React also enters through what the sidebar portals out, like the
        // thread search covering the window, which would peek it for good.
        const onSidebar = e.currentTarget.contains(e.target as Node);
        if (sidebar.hidden && onSidebar) sidebar.peekOpen();
      }}
      onMouseLeave={sidebar.hidden ? sidebar.peekClose : undefined}
    >
      <PaneResizer pane="sidebar" {...SIDEBAR_WIDTH} min={sidebarMin} />
      {pageList || computers}
      {/* Kept mounted under a page's list, so the threads keep their place. */}
      <div className="shell-sidebar-threads" hidden={!!(pageList || computers)}>
        {children}
      </div>
      {!!error && <ErrorBox error={error} />}
    </aside>
  );
}
