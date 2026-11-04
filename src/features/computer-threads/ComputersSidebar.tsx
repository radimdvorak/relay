import { useState, type ReactNode } from "react";
import { ChevronRight, Folder, FolderOpen } from "lucide-react";
import type {
  ComputerThreadProject,
  ComputerThreads,
} from "../../../shared/computer-threads";
import { useStoredState } from "../../lib/persisted-store";
import { ErrorBox } from "../../ui/ui";
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
        {inListOrder(computers).map((c) => (
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

/** The computer's name, and muted after it when it can't be reached. */
function ComputerName({ computer }: { computer: ComputerThreads }) {
  return (
    <span className="computer-name">
      {computer.name}
      {!computer.online && <small>offline</small>}
    </span>
  );
}

/** Why the computer lists no threads, in a word for its row; none when it lists some. */
function nothingListed(computer: ComputerThreads) {
  if (!computer.online) return "offline";
  if (computer.problem) return "update Relay";
  if (!computer.threads.length) return "no threads";
}

/** The computer's latest thread, and 0 when it lists none, for the order they're in. */
const latest = (computer: ComputerThreads) => computer.threads[0]?.updated ?? 0;

/** Those with threads first, latest first; offline ones last. */
const inListOrder = (computers: ComputerThreads[]) =>
  [...computers].sort(
    (a, b) => Number(b.online) - Number(a.online) || latest(b) - latest(a),
  );

/**
 * With several computers, each is a group like the projects' groups in
 * Threads; one that lists nothing is a dim line saying why.
 */
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
  const projects = (
    <ComputerProjects
      computer={computer}
      openChatId={openChatId}
      opening={opening}
      onOpen={onOpen}
    />
  );
  if (!heading) return projects;
  const why = nothingListed(computer);
  if (why)
    return (
      <section
        className="sb-folder computer-unlisted"
        aria-label={computer.name}
      >
        <div className="sb-folder-row" title={computer.problem}>
          <span className="sb-folder-toggle">
            <span>{computer.name}</span>
            <span className="sb-folder-count">{why}</span>
          </span>
        </div>
      </section>
    );
  return (
    <section className="sb-folder" aria-label={computer.name}>
      <div className="sb-folder-row">
        <button
          className="sb-folder-toggle"
          aria-label={`${open ? "Collapse" : "Expand"} ${computer.name}`}
          aria-expanded={open}
          onClick={onFold}
        >
          <ChevronRight size={11} />
          <span>{computer.name}</span>
          <span className="sb-folder-count">{computer.threads.length}</span>
        </button>
      </div>
      {open && <div className="sb-folder-body">{projects}</div>}
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
