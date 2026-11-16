import { useId } from "react";

const HAND =
  "M9 23.6V17a3 3 0 0 1 6 0v4.5c1.8 1.2 2.8 3 2.8 5v5a7.2 7.2 0 0 1-7.2 7.2h-2a7.2 7.2 0 0 1-7.2-7.2v-5.4a1.9 1.9 0 0 1 3.8 0v-1.3a1.9 1.9 0 0 1 3.8 0z";
const THUMB = "M17.6 29.6c-3.4 0-6-1.2-8.6-1.2a2.2 2.2 0 0 0-.8 4.2l4.8 2.2";
const TEETH =
  "M28.2 28.5h2.7v3.1a.9.9 0 0 1-.9.9h-.9a.9.9 0 0 1-.9-.9zM31.1 28.5h2.7v3.1a.9.9 0 0 1-.9.9H32a.9.9 0 0 1-.9-.9z";
const SKIN_EDGE = "#A65B0A";

function Face({ skin }: { skin: string }) {
  return (
    <>
      <circle
        cx="31"
        cy="23.5"
        r="15"
        fill={skin}
        stroke={SKIN_EDGE}
        strokeWidth="1"
      />
      <ellipse
        cx="25.5"
        cy="12.6"
        rx="5.2"
        ry="2.1"
        transform="rotate(-28 25.5 12.6)"
        fill="#fff"
        opacity=".4"
      />
      <path d="M24.5 28.5h13a6.5 6.5 0 0 1-13 0z" fill="#5A2A0C" />
      <path
        d="M27.4 33.4a4.6 3 0 0 1 7.2 0a6.5 6.5 0 0 1-7.2 0z"
        fill="#F0606B"
      />
      <path d={TEETH} fill="#fff" stroke="#D9CBB8" strokeWidth=".4" />
    </>
  );
}

function Glasses({ lens }: { lens: string }) {
  return (
    <>
      <g stroke="#231712" strokeWidth="2.2">
        <rect x="20" y="15.5" width="10" height="9" rx="3.4" fill={lens} />
        <rect x="32.5" y="15.5" width="10" height="9" rx="3.4" fill={lens} />
        <path d="M30 19h2.5M42.5 18.6l3-.9" />
      </g>
      <circle cx="26" cy="20.4" r="1.6" fill="#3A1F0B" />
      <circle cx="37.4" cy="20.4" r="1.6" fill="#3A1F0B" />
      <path
        d="M22.2 19.6l1.8-2.1M34.7 19.6l1.8-2.1"
        stroke="#fff"
        strokeWidth="1.1"
      />
    </>
  );
}

/** The raised finger; its own group so the peek can wag it. */
function Hand({ skin }: { skin: string }) {
  return (
    <g className="advisor-peek-hand">
      <path d={HAND} fill={skin} stroke={SKIN_EDGE} strokeWidth="1.1" />
      <path d={THUMB} stroke={SKIN_EDGE} strokeWidth="1.1" />
      <path
        d="M10.6 17.2a1.4 1.4 0 0 1 2.8 0v2h-2.8z"
        fill="#FFF3B8"
        opacity=".85"
      />
    </g>
  );
}

/** A nerd raising a finger: the advisor, about to say "well, actually". */
export function AdvisorIcon({ size = 30 }: { size?: number }) {
  // Gradients are found by id; each icon on the page needs its own.
  const id = useId();
  const skin = `${id}-skin`,
    lens = `${id}-lens`;
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 48 48"
      fill="none"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <defs>
        <radialGradient id={skin} cx=".36" cy=".28" r=".85">
          <stop offset="0" stopColor="#FFF27E" />
          <stop offset=".5" stopColor="#FFD33B" />
          <stop offset="1" stopColor="#F0941C" />
        </radialGradient>
        <linearGradient id={lens} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#FFFFFF" />
          <stop offset="1" stopColor="#9FD6FF" />
        </linearGradient>
      </defs>
      <Face skin={`url(#${skin})`} />
      <Glasses lens={`url(#${lens})`} />
      <Hand skin={`url(#${skin})`} />
    </svg>
  );
}
