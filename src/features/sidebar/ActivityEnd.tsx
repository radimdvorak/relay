import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Check } from "lucide-react";
import type { ComputerThreads } from "../../../shared/computer-threads";
import type { ChatTriage } from "../../../shared/projects";
import { api } from "../../lib/api";
import { useNow } from "../../lib/useNow";
import { ErrorBox } from "../../ui/ui";
import { ComputerThreadCard } from "../computer-threads/ComputerThreadCard";
import type { ListedThread } from "../computer-threads/ComputerThreadRow";
import { useComputerThreads } from "../computer-threads/useComputerThreads";
import { useOpenComputerThread } from "../computer-threads/useOpenComputerThread";
import { useOpenThreadId } from "../thread/arrival";
import { InboxZero } from "./InboxZero";
import { SnoozeMenu } from "./SnoozeMenu";
import { passesThere, useThreadFilter } from "./useThreadFilter";

/**
 * In its Activity there, not settled or snoozed, and not a thread of yours
 * here yet: once it is, it has a card of its own above.
 */
const goingOn = (t: ListedThread) =>
  t.section === "active" && (!t.here || t.here.peek);

/**
 * The end of Activity: what's going on in the paired computers' own
 * threads, and with nothing going on anywhere, inbox zero.
 */
export function ActivityEnd({ quietHere }: { quietHere: boolean }) {
  const { filter } = useThreadFilter();
  const listing = useComputerThreads(true);
  const openChatId = useOpenThreadId();
  const { open, opening, error } = useOpenComputerThread();
  const triage = useComputerTriage();
  const going = (listing.data ?? [])
    .flatMap((computer) =>
      computer.threads
        .filter((t) => goingOn(t) && passesThere(filter, computer.id, t))
        .map((thread) => ({ computer, thread })),
    )
    .sort((a, b) => b.thread.updated - a.thread.updated);
  if (!going.length) return quietHere ? <InboxZero /> : null;
  return (
    <div className="sb-cards elsewhere">
      {going.map(({ computer, thread }) => (
        <ComputerThreadCard
          key={`${computer.id}:${thread.id}`}
          thread={thread}
          project={projectName(computer, thread)}
          computer={computer.name}
          selected={!!openChatId && thread.here?.chatId === openChatId}
          opening={opening === thread.id}
          actions={
            <TriageButtons
              thread={thread}
              onTriage={(t) => triage.run(computer.id, thread.id, t)}
            />
          }
          onOpen={() => open(computer.id, thread.id)}
        />
      ))}
      {!!(error || triage.error) && <ErrorBox error={error ?? triage.error} />}
    </div>
  );
}

const projectName = (computer: ComputerThreads, thread: ListedThread) =>
  computer.projects.find((p) => p.id === thread.projectId)?.name ?? "";

/** Snooze, and Settle unless it waits on you: as the cards of threads here have them. */
function TriageButtons({
  thread,
  onTriage,
}: {
  thread: ListedThread;
  onTriage: (triage: ChatTriage) => void;
}) {
  const now = useNow(30_000);
  const waitsOnYou = thread.waiting || thread.asking || thread.blocked;
  return (
    <>
      <SnoozeMenu
        now={now}
        onSnooze={(until) => onTriage({ kind: "snooze", until })}
      />
      {!waitsOnYou && (
        <button
          className="sb-card-action"
          title={`${thread.running ? "Settle when done" : "Settle"} — hide until something new happens`}
          onClick={(e) => {
            e.stopPropagation();
            onTriage({
              kind: thread.running ? "settle-when-done" : "settle",
            });
          }}
        >
          <Check size={13} />
          Settle
        </button>
      )}
    </>
  );
}

/** Settles or snoozes the thread on its computer, then lists them again. */
function useComputerTriage() {
  const qc = useQueryClient();
  const [error, setError] = useState<unknown>();
  const run = (computerId: string, threadId: string, triage: ChatTriage) => {
    setError(undefined);
    api
      .triageComputerThread(computerId, threadId, triage)
      .then(() => qc.invalidateQueries({ queryKey: ["computer-threads"] }))
      .catch(setError);
  };
  return { run, error };
}
