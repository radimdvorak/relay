import type { ComputerThreads } from "../../../shared/computer-threads";
import { ErrorBox } from "../../ui/ui";
import { ComputerThreadCard } from "../computer-threads/ComputerThreadCard";
import type { ListedThread } from "../computer-threads/ComputerThreadRow";
import { useComputerThreads } from "../computer-threads/useComputerThreads";
import { useOpenComputerThread } from "../computer-threads/useOpenComputerThread";
import { useOpenThreadId } from "../thread/arrival";
import { InboxZero } from "./InboxZero";
import { passesThere, useThreadFilter } from "./useThreadFilter";

/**
 * Not settled there, and not a thread of yours here yet: once it is, it
 * has a card of its own above.
 */
const goingOn = (t: ListedThread) => !t.settledAt && (!t.here || t.here.peek);

/**
 * The end of Activity: what's going on in the paired computers' own
 * threads, and with nothing going on anywhere, inbox zero.
 */
export function ActivityEnd({ quietHere }: { quietHere: boolean }) {
  const { filter } = useThreadFilter();
  const listing = useComputerThreads(true);
  const openChatId = useOpenThreadId();
  const { open, opening, error } = useOpenComputerThread();
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
          onOpen={() => open(computer.id, thread.id)}
        />
      ))}
      {!!error && <ErrorBox error={error} />}
    </div>
  );
}

const projectName = (computer: ComputerThreads, thread: ListedThread) =>
  computer.projects.find((p) => p.id === thread.projectId)?.name ?? "";
