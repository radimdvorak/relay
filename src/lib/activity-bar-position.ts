import { fileSetting } from "./settings-file";
import { SIDEBAR_WIDTH } from "./settings-page";

/**
 * Where the activity bar sits: its own column at the window's left edge, or
 * a row of small icons along the sidebar's bottom, which comes and goes with
 * the sidebar. Kept in settings.json.
 */
export type BarPosition = "left" | "bottom";

const position = fileSetting<BarPosition>({
  path: ["activityBar", "position"],
  parse: (saved) => (saved === "bottom" ? "bottom" : "left"),
  serialize: (at) => (at === "bottom" ? at : undefined),
});

export const useBarPosition = position.use;
export const setBarPosition = position.set;

/** The bar's column at the window's left edge; activity-bar.css draws it this wide. */
const LEFT_BAR_WIDTH = 52;

/**
 * The sidebar's narrowest. Along its bottom the bar's column goes to the
 * sidebar, so the window's left column, the titlebar's brand above it,
 * never gets narrower than with the bar at the left edge.
 */
export function useSidebarMin() {
  const barAt = useBarPosition();
  return barAt === "bottom"
    ? SIDEBAR_WIDTH.min + LEFT_BAR_WIDTH
    : SIDEBAR_WIDTH.min;
}
