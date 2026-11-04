import type { ComputerThreads } from "../../../shared/computer-threads";
import { Spinner } from "../../ui/ui";

export type ListedThread = ComputerThreads["threads"][number];

/** Whether it waits on an answer from you: a question, an approval or a block. */
export const needsYou = (t: ListedThread) =>
  !!(t.waiting || t.asking || t.blocked);

/** Working or waiting on you, as the sidebar's own marks say it. */
function StatusMark({ thread }: { thread: ListedThread }) {
  if (needsYou(thread))
    return (
      <span className="sb-status waiting" title="Needs your input">
        <i />
      </span>
    );
  if (thread.running)
    return (
      <span className="sb-status running" title="Working">
        <Spinner size={11} steady />
      </span>
    );
  return null;
}

/** One of a paired computer's threads, in its project's list. */
export function ComputerThreadRow({
  thread,
  computer,
  selected,
  opening,
  onOpen,
}: {
  thread: ListedThread;
  computer: string;
  selected: boolean;
  /** Being followed from here for the first time. */
  opening: boolean;
  onOpen: (thread: ListedThread) => void;
}) {
  return (
    <div className="sb-thread-row">
      <button
        className={`sb-thread ${selected ? "selected" : ""}`}
        title={`${thread.title} · on ${computer}`}
        disabled={opening}
        aria-busy={opening || undefined}
        onClick={() => onOpen(thread)}
      >
        <span className="sb-thread-content">
          <span className="sb-thread-title">{thread.title}</span>
        </span>
        <StatusMark thread={thread} />
      </button>
    </div>
  );
}
