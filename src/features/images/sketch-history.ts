import type { SketchHistory, Stroke } from "./sketch";

/** `history` with `next` drawn on top of what's there; it can't be redone past. */
export const committed = (
  history: SketchHistory,
  next: (present: Stroke[]) => Stroke[],
): SketchHistory => ({
  past: [...history.past, history.present],
  present: next(history.present),
  future: [],
});

export const undone = (history: SketchHistory): SketchHistory =>
  history.past.length
    ? {
        past: history.past.slice(0, -1),
        present: history.past.at(-1)!,
        future: [history.present, ...history.future],
      }
    : history;

export const redone = (history: SketchHistory): SketchHistory =>
  history.future.length
    ? {
        past: [...history.past, history.present],
        present: history.future[0],
        future: history.future.slice(1),
      }
    : history;
