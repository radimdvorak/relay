import type { UsageMeter } from "../../../shared/provider-usage";
import { RING_RADIUS } from "./ContextWindowMeter";
import "./composer-model-picker.css";

// Outer ring is the long limit (the week, or Cursor's month), inner ring the
// session, so the two limits read at a glance and the icon differs from the
// single-ring context meter. A pixel larger than the context meter's so the
// button matches the send button's height with the same padding around the
// rings.
const RINGS = [
  { ring: "long", radius: RING_RADIUS + 1, kinds: ["weekly", "monthly"] },
  { ring: "session", radius: 5, kinds: ["session"] },
] as const;

/** The two rings on their own, as the trigger and Settings' sample draw them. */
export function UsageDial({
  meters,
}: {
  meters: Pick<UsageMeter, "kind" | "leftPercent" | "pace" | "model">[];
}) {
  // A single model's own limit isn't the plan's, so it gets no ring.
  const plan = meters.filter((m) => !m.model);
  return (
    <svg
      className="usage-ring-dial"
      width="20"
      height="20"
      viewBox="0 0 20 20"
      aria-hidden
    >
      {RINGS.map(({ ring, radius, kinds }) => {
        const circumference = 2 * Math.PI * radius;
        const meter = plan
          .filter((m) => (kinds as readonly string[]).includes(m.kind))
          .reduce<(typeof plan)[number] | undefined>(
            (worst, m) =>
              !worst || m.leftPercent < worst.leftPercent ? m : worst,
            undefined,
          );
        // Full while the limit is untouched, draining as it's used.
        const left = meter?.leftPercent ?? 0;
        return (
          <g key={ring} className="usage-ring" data-ring={ring}>
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
