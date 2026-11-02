import { useEffect, useRef } from "react";
import { reducedMotion } from "../../lib/slide-row";

const PINNED = ".sb-shelf-toggle, .sb-shelf-floor";

/**
 * The strip under Activity's pinned shelf headers. It also cuts off whatever
 * scrolls under those headers: over frosted glass they have no background,
 * so the cards would show through them.
 */
export function ShelfFloor() {
  const floor = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const scroller = floor.current?.parentElement;
    if (!scroller) return;
    const clip = () => clipUnderPinned(scroller);
    const sizes = new ResizeObserver(clip);
    const watchSizes = () => {
      sizes.disconnect();
      sizes.observe(scroller);
      for (const child of scroller.children) sizes.observe(child);
    };
    const children = new MutationObserver(watchSizes);
    children.observe(scroller, { childList: true });
    watchSizes();
    scroller.addEventListener("scroll", clip, { passive: true });
    return () => {
      scroller.removeEventListener("scroll", clip);
      children.disconnect();
      sizes.disconnect();
      for (const child of scroller.children)
        if (child instanceof HTMLElement) child.style.clipPath = "";
    };
  }, []);

  return <div ref={floor} className="sb-shelf-floor" />;
}

/** Hides the part of each block that sits under the next pinned header. */
function clipUnderPinned(scroller: HTMLElement) {
  let header: DOMRect | undefined;
  for (let at = scroller.lastElementChild; at; at = at.previousElementSibling) {
    if (!(at instanceof HTMLElement)) continue;
    if (at.matches(PINNED)) {
      header = at.getBoundingClientRect();
      continue;
    }
    const under = header ? at.getBoundingClientRect().bottom - header.top : 0;
    // Wide sides keep hovered cards' shadows and outlines.
    at.style.clipPath = under > 0 ? `inset(-100px -100px ${under}px)` : "";
  }
}

/**
 * Scrolls a shelf just opened from its pinned header into view: as much of
 * its list as fits above the headers pinned below it, its own header no
 * higher than the top.
 */
export function revealShelf(list: HTMLElement) {
  const scroller = list.parentElement;
  const header = list.previousElementSibling;
  if (!scroller || !(header instanceof HTMLElement)) return;
  let pinned = 0;
  for (let at = list.nextElementSibling; at; at = at.nextElementSibling)
    if (at instanceof HTMLElement && at.matches(PINNED))
      pinned += at.offsetHeight;
  const top =
    list.getBoundingClientRect().top -
    scroller.getBoundingClientRect().top +
    scroller.scrollTop;
  const to = Math.min(
    top + list.offsetHeight - (scroller.clientHeight - pinned),
    top - header.offsetHeight,
  );
  if (to > scroller.scrollTop)
    scroller.scrollTo({
      top: to,
      behavior: reducedMotion() ? "auto" : "smooth",
    });
}
