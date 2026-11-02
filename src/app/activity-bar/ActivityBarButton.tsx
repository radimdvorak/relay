import type { ReactNode } from "react";
import type { SidebarAttention } from "./ActivityBar";
import "./activity-bar.css";

/** One of the activity bar's icons: what it's called, shows and does. */
export interface BarEntry {
  label: string;
  icon: ReactNode;
  keys?: string;
  /** Something waiting there, which the dot marks and the tooltip names. */
  dot?: string;
  /** The dot in the sidebar's own look, for the sidebar's news. */
  status?: SidebarAttention;
  current?: boolean;
  onClick: () => void;
}

/** One of the activity bar's icons, with the dot for what waits there. */
export function BarButton({
  label,
  icon,
  keys = "",
  dot,
  status,
  current = false,
  onClick,
}: BarEntry) {
  return (
    <button
      type="button"
      className={`activity-bar-button ${current ? "active" : ""}`}
      aria-label={dot ? `${label} · ${dot}` : label}
      aria-current={current ? "page" : undefined}
      title={[`${label}  ${keys}`.trim(), dot].filter(Boolean).join(" · ")}
      onClick={onClick}
    >
      {icon}
      {dot && <BarDot status={status} />}
    </button>
  );
}

/** The dot on a button: the sidebar's own for its news, the accent's otherwise. */
export function BarDot({ status }: { status?: SidebarAttention }) {
  if (status)
    return (
      <span
        className={`sb-status ${status} activity-bar-status`}
        aria-hidden="true"
      >
        <i />
      </span>
    );
  return <span className="activity-bar-dot" aria-hidden="true" />;
}

/** A clock with a thread bubble: a thread that starts on a schedule. */
export function ScheduledThreadIcon({ size }: { size: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20 11a9 9 0 1 0-9 9" />
      <path d="M11 6v5h3.5" />
      <path d="M22 15a1 1 0 0 0-1-1h-6a1 1 0 0 0-1 1v7l2.5-2H21a1 1 0 0 0 1-1z" />
    </svg>
  );
}
