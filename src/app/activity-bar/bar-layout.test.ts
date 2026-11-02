import { describe, expect, it } from "vitest";
import {
  BOTTOM_BAR,
  barOrderFrom,
  defaultBarOrder,
  fitBar,
  placeSlot,
  type BarSlot,
} from "./bar-layout";

/** The width that holds `icons` icons along the bottom, Settings aside. */
const roomFor = (icons: number) => BOTTOM_BAR.fixed + icons * BOTTOM_BAR.step;
const everything = () => true;

describe("barOrderFrom", () => {
  it("keeps a saved order and adds what it lacks at the end", () => {
    expect(barOrderFrom(["usage", "fill", "threads"])).toEqual([
      "usage",
      "fill",
      "threads",
      "search",
      "pulls",
      "schedules",
      "clockify",
    ]);
  });

  it("drops names it doesn't know and repeats", () => {
    expect(barOrderFrom(["inbox", "usage", "usage", 3])).toEqual([
      "usage",
      ...defaultBarOrder.filter((slot) => slot !== "usage"),
    ]);
  });

  it("falls back to the default for anything but a list", () => {
    expect(barOrderFrom({ threads: 1 })).toEqual(defaultBarOrder);
  });
});

describe("placeSlot", () => {
  const order: BarSlot[] = ["threads", "search", "fill", "usage"];

  it("moves an icon across the fill to the far end", () => {
    expect(placeSlot(order, "search", "usage", true)).toEqual([
      "threads",
      "fill",
      "usage",
      "search",
    ]);
  });

  it("hands back the same order when nothing moves, so a drag doesn't re-render", () => {
    expect(placeSlot(order, "threads", "search", false)).toBe(order);
  });
});

describe("fitBar", () => {
  it("shows everything when it fits, with no menu", () => {
    const fit = fitBar([...defaultBarOrder], roomFor(6), everything);
    expect(fit).toEqual({ shown: defaultBarOrder, more: [] });
  });

  it("folds the last icons into the menu, which takes one icon's room", () => {
    const fit = fitBar([...defaultBarOrder], roomFor(5), everything);
    // Five places: the timer, three icons and "…".
    expect(fit.more).toEqual(["schedules", "usage"]);
    expect(fit.shown).toEqual([
      "threads",
      "search",
      "pulls",
      "fill",
      "clockify",
    ]);
  });

  it("never folds the timer, wherever it was dragged", () => {
    const order: BarSlot[] = [
      ...defaultBarOrder.filter((s) => s !== "clockify"),
      "clockify",
    ];
    const fit = fitBar(order, roomFor(3), everything);
    expect(fit.shown).toContain("clockify");
    // Three places: the timer, Threads and "…".
    expect(fit.more).toEqual(["search", "pulls", "schedules", "usage"]);
  });

  it("leaves out a timer that doesn't show, giving its room to the others", () => {
    const fit = fitBar(
      [...defaultBarOrder],
      roomFor(5),
      (item) => item !== "clockify",
    );
    expect(fit.more).toEqual([]);
    expect(fit.shown).not.toContain("clockify");
  });
});
