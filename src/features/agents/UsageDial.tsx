import { dialRings, type UsageMeter } from "../../../shared/provider-usage";
import { RING_RADIUS } from "./ContextWindowMeter";
import "./composer-model-picker.css";

// Outer ring is the long limit (or the lone limit when only one applies), inner
// ring the session (or Cursor's API sub-pool). A pixel larger than the context
// meter's so the button matches the send button's height with the same padding
// around the rings.
const OUTER_RADIUS = RING_RADIUS + 1;
const INNER_RADIUS = 5;

const LOADING_RINGS = [
  { role: "long", radius: OUTER_RADIUS },
  { role: "session", radius: INNER_RADIUS },
] as const;

/** The rings on their own, as the trigger and Settings' sample draw them. */
export function UsageDial({
  meters,
}: {
  meters: Pick<UsageMeter, "id" | "kind" | "leftPercent" | "pace" | "model">[];
}) {
  const rings = dialRings(meters);
  const drawn = rings.length
    ? rings.map((entry, index) => ({
        role: entry.role,
        radius: index === 0 ? OUTER_RADIUS : INNER_RADIUS,
        meter: entry.meter,
      }))
    : LOADING_RINGS.map((ring) => ({ ...ring, meter: undefined }));
  return (
    <svg
      className="usage-ring-dial"
      width="20"
      height="20"
      viewBox="0 0 20 20"
      aria-hidden
    >
      {drawn.map(({ role, radius, meter }) => {
        const circumference = 2 * Math.PI * radius;
        // Full while the limit is untouched, draining as it's used.
        const left = meter?.leftPercent ?? 0;
        return (
          <g key={`${role}:${radius}`} className="usage-ring" data-ring={role}>
            <circle className="usage-ring-track" cx="10" cy="10" r={radius} />
            <circle
              className="usage-ring-fill"
              data-pace={meter?.pace ?? "ok"}
              cx="10"
              cy="10"
              r={radius}
              strokeDasharray={circumference}
              strokeDashoffset={circumference * (1 - left / 100)}
            />
          </g>
        );
      })}
    </svg>
  );
}
