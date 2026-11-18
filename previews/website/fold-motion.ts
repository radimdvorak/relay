// Opening the foldable the way github.com/jadon7/iphone-duo does it: only the
// flap swings, and the picture on it never turns with it. Each screen's UI
// stays where it sits when that screen is flat; the swinging panel only
// decides how much of it shows, so nothing on screen jumps or squashes. The
// further the panel stands from flat, the more its far edge blurs and darkens.
import { useLayoutEffect, useRef, type RefObject } from "react";
import { reducedMotion } from "./motion";

/** A full swing, folded to open. */
const DURATION = 1100;

/** Frame plus glass between a half's edge and its screen, in px. */
const BEZEL = 6;

/** The blur at the far edge of a panel standing on end, in px. */
const MAX_BLUR = 18;

/** Where the progressive blur's three layers start, from the hinge (0) to the far edge (1). */
const BANDS = [0.4, 0.65, 0.9];

type Point = { x: number; y: number };

/** Where the panel's screen edges land on the page, and how far it stands from flat. */
type Panel = { hingeSide: [Point, Point]; farSide: [Point, Point]; motion: number };

const smoothstep = (t: number) => {
  const x = Math.min(1, Math.max(0, t));
  return x * x * (3 - 2 * x);
};

const easeInOut = (t: number) => (1 - Math.cos(Math.PI * t)) / 2;

const polygon = (points: Point[]) => `polygon(${points.map((p) => `${p.x.toFixed(2)}px ${p.y.toFixed(2)}px`).join(", ")})`;

/**
 * The flap's screen after swinging `bend` of the way shut (0 open, 1 folded),
 * seen through the fold's perspective. Its hinge sits on the perspective origin.
 */
function panelAt(bend: number, width: number, height: number, perspective: number): Panel {
  const hinge = width / 2;
  const middle = height / 2;
  const angle = bend * Math.PI;
  const edgeAt = (distance: number): [Point, Point] => {
    const scale = perspective / (perspective - distance * Math.sin(angle));
    const x = hinge - distance * Math.cos(angle) * scale;
    const reach = (middle - BEZEL) * scale;
    return [
      { x, y: middle - reach },
      { x, y: middle + reach },
    ];
  };
  const innerFaceShows = bend < 0.5;
  // The inner screen runs on across the hinge; the cover screen has a bezel there.
  const hingeSide = innerFaceShows ? edgeAt(0) : edgeAt(BEZEL * bend);
  const motion = innerFaceShows ? smoothstep(bend * 2) : smoothstep((1 - bend) * 2);
  return { hingeSide, farSide: edgeAt(hinge - BEZEL), motion };
}

/** A left-to-right gradient whose stops are given from the hinge side (0) to the far side (1). */
function gradientAcross(panel: Panel, stops: [number, string][]) {
  const from = panel.hingeSide[0].x;
  const to = panel.farSide[0].x;
  const placed = stops.map(([along, color]) => ({ x: from + along * (to - from), color }));
  placed.sort((a, b) => a.x - b.x);
  return `linear-gradient(to right, ${placed.map((s) => `${s.color} ${s.x.toFixed(2)}px`).join(", ")})`;
}

/** How dark the panel gets at each point from the hinge, as the iPhone Duo study darkens it. */
function shadeFor(panel: Panel) {
  const stops: [number, string][] = [0, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1].map((along) => {
    const reach = Math.max(0, (along - 0.2) / 0.8) ** 1.35;
    const alpha = Math.min(1, 2 * panel.motion * reach);
    return [along, `rgb(0 0 0 / ${alpha.toFixed(3)})`];
  });
  return gradientAcross(panel, stops);
}

/** Writes where everything sits at `bend` as custom properties on the stage. */
function paint(stage: HTMLElement, fold: HTMLElement, bend: number) {
  const width = fold.offsetWidth;
  const height = fold.offsetHeight;
  const perspective = parseFloat(getComputedStyle(fold).perspective) || 1700;
  const panel = panelAt(bend, width, height, perspective);
  const hinge = width / 2;
  const [hingeTop, hingeBottom] = panel.hingeSide;
  const [farTop, farBottom] = panel.farSide;
  const style = stage.style;
  style.setProperty("--bend", bend.toFixed(4));
  style.setProperty("--panel-clip", polygon([farTop, hingeTop, hingeBottom, farBottom]));

  // The inner screen's right half lies flat under the flap and always shows
  // where the flap isn't; its left half shows wherever the flap's inner face does.
  const innerFaceShows = bend < 0.5;
  const left = innerFaceShows ? panel.farSide : panel.hingeSide;
  style.setProperty(
    "--inner-clip",
    polygon([
      left[0],
      { x: hinge, y: BEZEL },
      { x: hinge, y: 0 },
      { x: width, y: 0 },
      { x: width, y: height },
      { x: hinge, y: height },
      { x: hinge, y: height - BEZEL },
      left[1],
    ]),
  );

  // The cover screen rides on the flap's back, held to its hinge-side edge.
  style.setProperty("--cover-clip", innerFaceShows ? "inset(50%)" : polygon([hingeTop, farTop, farBottom, hingeBottom]));
  style.setProperty("--cover-shift", `${innerFaceShows ? 0 : (hingeTop.x - hinge - BEZEL).toFixed(2)}px`);

  stage.toggleAttribute("data-inner-face", innerFaceShows);
  const shaded = panel.motion > 0.005;
  stage.toggleAttribute("data-shaded", shaded);
  if (!shaded) return;
  style.setProperty("--shade", shadeFor(panel));
  BANDS.forEach((start, index) => {
    const blur = MAX_BLUR * panel.motion * start ** 1.35;
    style.setProperty(`--blur-${index + 1}`, `${blur.toFixed(2)}px`);
    style.setProperty(
      `--band-${index + 1}`,
      gradientAcross(panel, [
        [start - 0.25, "transparent"],
        [start, "#000"],
      ]),
    );
  });
}

/**
 * Swings the foldable in `fold` open or shut. Everything that moves with it
 * reads `--bend` and the clips this sets on `stage`.
 */
export function useFoldMotion(
  open: boolean,
  stage: RefObject<HTMLElement | null>,
  fold: RefObject<HTMLElement | null>,
) {
  const bend = useRef(open ? 0 : 1);
  useLayoutEffect(() => {
    const stageElement = stage.current;
    const foldElement = fold.current;
    if (!stageElement || !foldElement) return;
    const target = open ? 0 : 1;
    const from = bend.current;
    const settle = () => {
      bend.current = target;
      paint(stageElement, foldElement, target);
    };
    if (from === target || reducedMotion()) return settle();
    // A swing reversed halfway only has the rest of the way to go.
    const duration = DURATION * Math.abs(target - from);
    const start = performance.now();
    let frame = 0;
    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / duration);
      bend.current = from + (target - from) * easeInOut(progress);
      paint(stageElement, foldElement, bend.current);
      if (progress < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [open, stage, fold]);
}
