import { useEffect, useRef, useState, type DragEvent } from "react";
import { placeSlot, setBarOrder, type BarSlot } from "./bar-layout";

interface Drag {
  slot: BarSlot;
  from: BarSlot[];
  order: BarSlot[];
}

/**
 * Dragging the bar's icons into a new order. The order changes under the
 * pointer as it goes and is saved once, when the icon is let go: saving on
 * every dragover would rewrite settings.json dozens of times a drag.
 */
export function useBarDrag(saved: BarSlot[], vertical: boolean) {
  const [drag, setDrag] = useState<Drag>();
  // Handlers in one event see each other's changes before React renders.
  const current = useRef(drag);
  const update = (next: Drag | undefined) => {
    current.current = next;
    setDrag(next);
  };
  const dragging = drag !== undefined;
  useEffect(() => {
    if (!dragging) return;
    const end = () => {
      const done = current.current;
      update(undefined);
      if (done && done.order !== done.from) setBarOrder(done.order);
    };
    const events = ["dragend", "drop"] as const;
    for (const event of events) window.addEventListener(event, end);
    return () => {
      for (const event of events) window.removeEventListener(event, end);
    };
  }, [dragging]);

  const place = (target: BarSlot, after: boolean) => {
    const now = current.current;
    if (!now) return;
    const order = placeSlot(now.order, now.slot, target, after);
    if (order !== now.order) update({ ...now, order });
  };

  /** Whether the pointer is past the middle of what it's over, along the bar. */
  const pastMiddle = (e: DragEvent) => {
    const box = e.currentTarget.getBoundingClientRect();
    return vertical
      ? e.clientY > box.top + box.height / 2
      : e.clientX > box.left + box.width / 2;
  };

  /** What something in the bar needs for an icon to be dropped beside it. */
  const over = (slot: BarSlot) => ({
    onDragOver(e: DragEvent) {
      if (!current.current) return;
      e.preventDefault();
      if (current.current.slot !== slot) place(slot, pastMiddle(e));
    },
  });

  return {
    order: drag?.order ?? saved,
    moving: drag?.slot,
    over,
    /** What an icon's wrapper needs to drag, and to be dropped beside. */
    handlers(slot: BarSlot) {
      return {
        ...over(slot),
        onDragStart(e: DragEvent) {
          e.dataTransfer.effectAllowed = "move";
          // Not text, or letting go over the composer would type the name in.
          e.dataTransfer.setData("application/x-relay-bar-icon", slot);
          update({ slot, from: saved, order: saved });
        },
      };
    },
  };
}
