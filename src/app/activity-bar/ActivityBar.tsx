import { useCallback, useLayoutEffect, useState, type ReactNode } from "react";
import {
  ChartColumn,
  GitPullRequest,
  MessagesSquare,
  Search,
  Settings2,
} from "lucide-react";
import { ClockifyTimer } from "../../features/plugins/ClockifyTimer";
import {
  useClockifyStatus,
  usePluginEnabled,
} from "../../features/plugins/plugins";
import { openThreadSearch } from "../../features/sidebar/thread-search";
import { usePutOffRelayUpdate } from "../../features/updates/put-off";
import type { PullsTarget } from "../../features/pulls/pull-board";
import type { useSettingsPage } from "../../features/settings/useSettingsPage";
import { useBarPosition } from "../../lib/activity-bar-position";
import type { NavigationLock } from "../../lib/navigation-lock";
import { useShortcutLabel } from "../../lib/shortcuts";
import type { ShellNavigation, Surface } from "../useShellNavigation";
import type { SidebarVisibility } from "../useSidebarVisibility";
import {
  BarButton,
  ScheduledThreadIcon,
  type BarEntry,
} from "./ActivityBarButton";
import { BarMore } from "./BarMore";
import { fitBar, useBarOrder, type BarSlot, type MenuItem } from "./bar-layout";
import { useBarDrag } from "./useBarDrag";

/** The sidebar's unread / needs-input dot. */
export type SidebarAttention = "waiting" | "unread";

/** What the sidebar's dot says, for a tooltip. */
export function sidebarDotSays(attention: SidebarAttention | undefined) {
  if (attention === "waiting") return "Needs your input";
  if (attention === "unread") return "New activity";
}

/** What the main pane shows, so the bar marks its button. */
type BarPage = "threads" | "inbox" | "usage" | "schedules" | "settings";

interface BarProps {
  nav: ShellNavigation;
  settings: ReturnType<typeof useSettingsPage>;
  sidebar: SidebarVisibility;
  lock: NavigationLock;
  /** Moves within the open Pull requests page, from a PR back to its board. */
  goToPulls: (target: PullsTarget) => void;
  onUsage: () => void;
  onSchedules: () => void;
  attention?: SidebarAttention;
}

function barPageFor(surface: Surface, settingsOpen: boolean): BarPage {
  if (settingsOpen) return "settings";
  return surface === "project" ? "threads" : surface;
}

/** Which button is lit, and where each button goes. */
function barNavigation(props: BarProps) {
  const { nav, settings, sidebar, lock, goToPulls, onUsage, onSchedules } =
    props;
  const page = barPageFor(nav.surface, settings.open);
  // Settings covers the page but not the bar, so the page's own button
  // uncovers it rather than moving it.
  const coveredBySettings = (surface: Surface) =>
    settings.open && nav.surface === surface;
  return {
    page,
    /** Back to the threads from a page or Settings; on them already, the sidebar folds. */
    showThreads() {
      if (page === "threads") return sidebar.toggle();
      if (lock.blocked()) return;
      settings.close();
      nav.setSurface("project");
    },
    /** From the page itself back to its board; from anywhere else to where it was left. */
    openPulls() {
      if (coveredBySettings("inbox")) return settings.close();
      if (lock.blocked()) return;
      if (nav.inbox) goToPulls({ to: "board" });
      else nav.setInbox(true);
    },
    toggleUsage() {
      if (coveredBySettings("usage")) settings.close();
      else onUsage();
    },
    toggleSchedules() {
      if (coveredBySettings("schedules")) settings.close();
      else onSchedules();
    },
    toggleSettings() {
      if (settings.open) settings.close();
      else settings.show();
    },
  };
}

/** The bar's buttons by name; the timer is drawn on its own. */
function useBarEntries(
  props: BarProps,
  bar: ReturnType<typeof barNavigation>,
  iconSize: number,
): Record<MenuItem | "settings", BarEntry> {
  const { page } = bar;
  const searchKeys = useShortcutLabel("search-threads");
  const settingsKeys = useShortcutLabel("settings");
  const putOffUpdate = usePutOffRelayUpdate();
  return {
    threads: {
      label: "Threads",
      icon: <MessagesSquare size={iconSize} />,
      // The sidebar shows its own dots; hidden, Threads carries them.
      dot: props.sidebar.hidden ? sidebarDotSays(props.attention) : undefined,
      status: props.attention,
      current: page === "threads",
      onClick: bar.showThreads,
    },
    search: {
      label: "Search threads",
      icon: <Search size={iconSize} />,
      keys: searchKeys,
      onClick: openThreadSearch,
    },
    pulls: {
      label: "Pull requests",
      icon: <GitPullRequest size={iconSize} />,
      current: page === "inbox",
      onClick: bar.openPulls,
    },
    schedules: {
      label: "Schedules",
      icon: <ScheduledThreadIcon size={iconSize} />,
      current: page === "schedules",
      onClick: bar.toggleSchedules,
    },
    usage: {
      label: "Usage",
      icon: <ChartColumn size={iconSize} />,
      current: page === "usage",
      onClick: bar.toggleUsage,
    },
    settings: {
      label: "Open settings",
      icon: <Settings2 size={iconSize} />,
      keys: settingsKeys,
      dot: putOffUpdate,
      current: page === "settings",
      onClick: bar.toggleSettings,
    },
  };
}

/** The bar's width with its padding, which BOTTOM_BAR counts, for what fits along the bottom. */
function useWidth(element: HTMLElement | null) {
  const [width, setWidth] = useState(0);
  useLayoutEffect(() => {
    if (!element) return;
    const observer = new ResizeObserver(([entry]) =>
      setWidth(Math.round(entry.borderBoxSize[0].inlineSize)),
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [element]);
  return width;
}

/** Whether Clockify's timer shows, which takes a place in the bar. */
function useTimerShows() {
  const on = usePluginEnabled("clockify");
  return on && !!useClockifyStatus(on).data;
}

/**
 * Which icons show and which wait in the "…" menu. On the left there's
 * height for all of them; along the bottom it's what the width holds,
 * and everything until it's measured rather than flash the menu.
 */
function useBarFit(
  bottom: boolean,
  order: BarSlot[],
  barRef: SidebarVisibility["barRef"],
) {
  const [element, setElement] = useState<HTMLElement | null>(null);
  const ref = useCallback(
    (nav: HTMLElement | null) => {
      barRef.current = nav;
      setElement(nav);
    },
    [barRef],
  );
  const width = useWidth(bottom ? element : null);
  const timerShows = useTimerShows();
  const room = bottom && width ? width : Infinity;
  const fit = fitBar(order, room, (item) => item !== "clockify" || timerShows);
  return { ref, fit };
}

/**
 * The threads, thread search and the pages that open in place of a project,
 * in the order they were dragged into, and Settings last after a line. On
 * the left it's a column down the window's edge; along the sidebar's bottom
 * it's a row of small icons, and what doesn't fit goes in a "…" menu.
 */
export function ActivityBar(props: BarProps) {
  const { nav, settings, sidebar } = props;
  const position = useBarPosition();
  const bottom = position === "bottom";
  const bar = barNavigation(props);
  const entries = useBarEntries(props, bar, bottom ? 16 : 19);
  const drag = useBarDrag(useBarOrder(), !bottom);
  const { ref, fit } = useBarFit(bottom, drag.order, sidebar.barRef);
  // Time spent on Pull requests, Usage or Schedules isn't the project's.
  const tracked = nav.surface === "project" ? nav : undefined;
  const folded = sidebar.barFolded;
  const away = folded && !sidebar.peek;
  const timer = (
    <ClockifyTimer
      projectId={tracked?.project?.id}
      projectName={tracked?.project?.name}
      chatId={tracked?.chat?.id}
      side={bottom ? "top" : "right"}
      onSetUp={() => settings.show("integrations")}
    />
  );

  const slot = (name: BarSlot): ReactNode => {
    if (name === "fill")
      return (
        <span key="fill" className="activity-bar-fill" {...drag.over(name)} />
      );
    return (
      <span
        key={name}
        className="activity-bar-slot"
        draggable
        data-moving={drag.moving === name || undefined}
        {...drag.handlers(name)}
      >
        {name === "clockify" ? timer : <BarButton {...entries[name]} />}
      </span>
    );
  };

  return (
    <nav
      ref={ref}
      className={`activity-bar ${position} ${folded ? "folded" : ""} ${sidebar.peek ? "peek" : ""}`}
      aria-label="Activity bar"
      aria-hidden={away || undefined}
      inert={away || undefined}
      data-dragging={drag.moving ? true : undefined}
      onMouseEnter={folded ? sidebar.peekOpen : undefined}
      onMouseLeave={folded ? sidebar.peekClose : undefined}
    >
      {fit.shown.map(slot)}
      {fit.more.length > 0 && (
        <BarMore items={fit.more.map((name) => entries[name])} />
      )}
      <span className="activity-bar-line" aria-hidden="true" />
      <BarButton {...entries.settings} />
    </nav>
  );
}
