import { Check } from "lucide-react";

/** Activity with nothing in it: no thread waits on you, no draft is unsent. */
export function InboxZero() {
  return (
    <div className="sb-empty">
      <span className="sb-empty-icon">
        <Check size={18} />
      </span>
      <strong>Inbox zero</strong>
      <p>Threads come back here when an agent replies or needs you.</p>
    </div>
  );
}
