import { useState, type ReactNode } from "react";
import { ChevronRight, Folder, FolderOpen } from "lucide-react";
import type {
  ComputerThreadProject,
  ComputerThreads,
} from "../../../shared/computer-threads";
import { useStoredState } from "../../lib/persisted-store";
import { ErrorBox } from "../../ui/ui";
import { DeviceIcon } from "../handoff/DeviceIcon";
import { useOpenThreadId } from "../thread/arrival";
import { ComputerThreadRow, type ListedThread } from "./ComputerThreadRow";
import { useComputerThreads } from "./useComputerThreads";
import { useOpenComputerThread } from "./useOpenComputerThread";
import "./computer-threads.css";

/**
 * Every thread on the paired computers, the ones each started itself too,
 * by computer and project. With several computers each folds away.
 */
export function ComputersSidebar() {
  const listing = useComputerThreads(true);
  const computers = listing.data ?? [];
  const openChatId = useOpenThreadId();
  const { open, opening, error } = useOpenComputerThread();
  const [folded, setFolded] = useStoredState(
    "relay-computers-folded",
    (saved) =>
      Array.isArray(saved) ? saved.filter((s) => typeof s === "string") : [],
  );
  const one = computers.length === 1 ? computers[0] : undefined;
  const fold = (id: string) =>
    setFolded((ids) =>
      ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id],
    );
  return (
    <div className="sb computers-sidebar">
      <div className="sb-top">
        <h2 className="sb-top-title">
          {one ? <ComputerName computer={one} /> : "Computers"}
        </h2>
      </div>
      <div className="sb-scroll">
        {computers.map((c) => (
          <ComputerSection
            key={c.id}
            computer={c}
            // One computer is the whole sidebar, named at its top.
            heading={!one}
            open={!folded.includes(c.id)}
            onFold={() => fold(c.id)}
            openChatId={openChatId}
            opening={opening}
            onOpen={(thread) => open(c.id, thread.id)}
          />
        ))}
        {!!error && <ErrorBox error={error} />}
        {listing.isSuccess && !computers.length && (
          <p className="sb-note">Pair a computer in Settings → Computers.</p>
        )}
        {!!listing.error && <ErrorBox error={listing.error} />}
      </div>
    </div>
  );
}

/** The computer's name, and muted after it whether it's reachable. */
function ComputerName({ computer }: { computer: ComputerThreads }) {
  return (
    <span className="computer-name">
      {computer.name}
      <small>{computer.online ? "online" : "offline"}</small>
    </span>
  );
}

function ComputerSection({
  computer,
  heading,
  open,
  onFold,
  openChatId,
  opening,
  onOpen,
}: {
  computer: ComputerThreads;
  heading: boolean;
  open: boolean;
  onFold: () => void;
  openChatId?: string;
  opening?: string;
  onOpen: (thread: ListedThread) => void;
}) {
  return (
    <section className="computer-section" aria-label={computer.name}>
      {heading && (
        <div className="sb-section-heading">
          <h2>
            <button
              className="sb-section-toggle"
              aria-expanded={open}
              onClick={onFold}
            >
              <DeviceIcon name={computer.name} size={13} />
              <ComputerName computer={computer} />
              <ChevronRight
                size={11}
                className="sb-project-chevron"
                data-open={open || undefined}
                aria-hidden
              />
            </button>
          </h2>
        </div>
      )}
      {(open || !heading) && (
        <ComputerProjects
          computer={computer}
          openChatId={openChatId}
          opening={opening}
          onOpen={onOpen}
        />
      )}
    </section>
  );
}

function ComputerProjects({
  computer,
  openChatId,
  opening,
  onOpen,
}: {
  computer: ComputerThreads;
  openChatId?: string;
  opening?: string;
  onOpen: (thread: ListedThread) => void;
}) {
  if (computer.problem) return <p className="sb-note">{computer.problem}</p>;
  if (!computer.online)
    return <p className="sb-note">Its threads show once it's back online.</p>;
  if (!computer.threads.length)
    return <p className="sb-note">No threads on {computer.name} yet.</p>;
  // Its projects in the order of their latest thread.
  const withThreads = computer.projects
    .map((p) => ({
      project: p,
      threads: computer.threads.filter((t) => t.projectId === p.id),
    }))
    .filter((p) => p.threads.length)
    .sort((a, b) => b.threads[0]!.updated - a.threads[0]!.updated);
  return withThreads.map(({ project, threads }) => (
    <ComputerProject key={project.id} project={project}>
      {threads.map((t) => (
        <ComputerThreadRow
          key={t.id}
          thread={t}
          computer={computer.name}
          selected={!!openChatId && t.here?.chatId === openChatId}
          opening={opening === t.id}
          onOpen={onOpen}
        />
      ))}
    </ComputerProject>
  ));
}

function ComputerProject({
  project,
  children,
}: {
  project: ComputerThreadProject;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(true);
  return (
    <section className="sb-project">
      <div className="sb-project-row">
        <button
          className="sb-project-expand"
          aria-label={`${open ? "Collapse" : "Expand"} ${project.name}`}
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          {open ? <FolderOpen size={15} /> : <Folder size={15} />}
        </button>
        <button
          className="sb-project-name"
          title={project.folder}
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          <span>{project.name}</span>
        </button>
      </div>
      {open && <div className="sb-thread-list">{children}</div>}
    </section>
  );
}
