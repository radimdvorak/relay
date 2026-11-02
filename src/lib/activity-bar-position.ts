import { fileSetting } from "./settings-file";

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
