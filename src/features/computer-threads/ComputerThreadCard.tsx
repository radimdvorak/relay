import { Monitor } from "lucide-react";
import { shortAge } from "../../../shared/chat-activity";
import type { Project } from "../../../shared/projects";
import { useNow } from "../../lib/useNow";
import { rowKeys, Spinner } from "../../ui/ui";
import { ProjectBadge } from "../projects/ProjectBadge";
import { needsYou, type ListedThread } from "./ComputerThreadRow";

/** What a card says of how the thread stands there. */
function CardState({ thread }: { thread: ListedThread }) {
  const now = useNow(30_000);
  if (needsYou(thread))
    return (
      <span className="sb-card-state waiting">
        <i />
        <span className="sb-card-label">Needs input</span>
      </span>
    );
  if (thread.running)
    return (
      <span className="sb-card-state running" title="Working">
        <Spinner size={11} steady />
      </span>
    );
  return <time className="sb-card-state">{shortAge(thread.updated, now)}</time>;
}

/**
 * One of a paired computer's own threads in Activity, as its cards look:
 * its project there, how it stands, and the computer it's on.
 */
export function ComputerThreadCard({
  thread,
  project,
  computer,
  selected,
  opening,
  onOpen,
}: {
  thread: ListedThread;
  /** Its project's name there. */
  project: string;
  computer: string;
  selected: boolean;
  opening: boolean;
  onOpen: () => void;
}) {
  // Its project here, once a thread here follows it, has the icon.
  const badge = {
    id: thread.here?.projectId ?? "",
    name: project,
  } as Project;
  const quiet = !needsYou(thread) && !thread.running;
  return (
    <div
      role="button"
      tabIndex={0}
      className={["sb-card", selected && "selected", quiet && "dim"]
        .filter(Boolean)
        .join(" ")}
      aria-busy={opening || undefined}
      onClick={onOpen}
      onKeyDown={rowKeys(onOpen)}
    >
      <div className="sb-card-top">
        <ProjectBadge project={badge} />
        <span className="sb-card-name">
          <span className="sb-card-project">{project}</span>
        </span>
        <CardState thread={thread} />
      </div>
      <span className="sb-card-title">{thread.title}</span>
      <div className="sb-card-meta">
        <span className="sb-thread-ref" />
        <span className="sb-card-where" title={`On ${computer}`}>
          <Monitor size={11} />
          {computer}
        </span>
      </div>
    </div>
  );
}
