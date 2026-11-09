import type { PeerRef } from "../../../shared/peer-messages";
import { useStartedThreads } from "../agent-turn/StartedThreads";
import "./peer-strip.css";

/** An answer's header note when Claude started the turn: who woke it, when another session did. */
export function StartedOnItsOwn({ wokenBy }: { wokenBy?: PeerRef }) {
  const view = useStartedThreads();
  if (!wokenBy)
    return (
      <span
        className="muted"
        title="Claude started this turn itself, for example when a background task finished."
      >
        started on its own
      </span>
    );
  const thread = wokenBy.threadId
    ? view?.threads.find((c) => c.id === wokenBy.threadId)
    : undefined;
  const name = thread?.title ?? wokenBy.name;
  return (
    <span className="muted">
      woken by{" "}
      {thread && view ? (
        <button
          type="button"
          className="message-woken-by"
          title={`Open ${thread.title}`}
          onClick={() => view.open(thread)}
        >
          {name}
        </button>
      ) : (
        name
      )}
    </span>
  );
}
