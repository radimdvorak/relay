import { fileSetting } from "../../lib/settings-file";

/** The activity bar's icons that move; Settings stays last. */
export const barItems = [
  "threads",
  "computers",
  "search",
  "pulls",
  "schedules",
  "clockify",
  "usage",
] as const;

export type BarItem = (typeof barItems)[number];
/** The timer never folds into the "…" menu: it shows the day's state and has its own panel. */
export type MenuItem = Exclude<BarItem, "clockify">;
/** "fill" is the stretch between the two ends: what follows it sits at the far end. */
export type BarSlot = BarItem | "fill";

export const defaultBarOrder: readonly BarSlot[] = [
  "threads",
  "computers",
  "search",
  "pulls",
  "schedules",
  "fill",
  "clockify",
  "usage",
];

const isSlot = (value: unknown): value is BarSlot =>
  value === "fill" || barItems.includes(value as BarItem);

/**
 * The order kept in settings.json, made whole: unknown names and repeats
 * drop out, and what's missing (a new icon, or the fill) joins at the end.
 */
export function barOrderFrom(saved: unknown): BarSlot[] {
  const known = Array.isArray(saved) ? saved.filter(isSlot) : [];
  const order = [...new Set(known)];
  for (const slot of defaultBarOrder)
    if (!order.includes(slot)) order.push(slot);
  return order;
}

const sameOrder = (a: readonly BarSlot[], b: readonly BarSlot[]) =>
  a.length === b.length && a.every((slot, i) => slot === b[i]);

const order = fileSetting<BarSlot[]>({
  path: ["activityBar", "order"],
  parse: barOrderFrom,
  serialize: (slots) => (sameOrder(slots, defaultBarOrder) ? undefined : slots),
});

export const useBarOrder = order.use;
export const setBarOrder = order.set;

/** Puts `moving` just before or after `target`; the same order back when that changes nothing. */
export function placeSlot(
  order: BarSlot[],
  moving: BarSlot,
  target: BarSlot,
  after: boolean,
): BarSlot[] {
  if (moving === target) return order;
  const next = order.filter((slot) => slot !== moving);
  next.splice(next.indexOf(target) + (after ? 1 : 0), 0, moving);
  return sameOrder(next, order) ? order : next;
}

/**
 * The bar along the sidebar's bottom, in pixels. The icons are all one
 * size, so what fits is a count; activity-bar.css draws them at this size.
 */
export const BOTTOM_BAR = {
  /** An icon and the gap after it. */
  step: 30,
  /** Padding at both ends, Settings and the line before it. */
  fixed: 12 + 28 + 9,
};

export interface BarFit {
  /** The slots to draw, in order. */
  shown: BarSlot[];
  /** The ones that didn't fit, for the "…" menu. */
  more: MenuItem[];
}

/**
 * What fits in `width`, from the start of the order, the timer always.
 * When anything is left over, the "…" button takes one icon's room.
 */
export function fitBar(
  order: BarSlot[],
  width: number,
  present: (item: BarItem) => boolean,
): BarFit {
  const items = order.filter(
    (slot): slot is BarItem => slot !== "fill" && present(slot),
  );
  const room = Math.floor((width - BOTTOM_BAR.fixed) / BOTTOM_BAR.step);
  const all = order.filter((slot) => slot === "fill" || present(slot));
  if (items.length <= room) return { shown: all, more: [] };
  const timer = items.includes("clockify") ? 1 : 0;
  const foldable = items.filter(
    (item): item is MenuItem => item !== "clockify",
  );
  const kept = Math.max(0, room - 1 - timer);
  const more = foldable.slice(kept);
  const folded = new Set<BarSlot>(more);
  return { shown: all.filter((slot) => !folded.has(slot)), more };
}
