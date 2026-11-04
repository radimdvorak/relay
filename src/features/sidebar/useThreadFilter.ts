import type { ComputerThread } from "../../../shared/computer-threads";
import { liveAway } from "../../../shared/handoff";
import type { ProjectFolderNode } from "../../../shared/project-folders";
import type { AgentProvider, ChatSummary } from "../../../shared/projects";
import { persistedStore } from "../../lib/persisted-store";

/** Which threads the sidebar lists; an empty list lets every one through. */
export interface ThreadFilter {
  projects: string[];
  agents: AgentProvider[];
  /** Paired computers by id, and THIS_COMPUTER. */
  computers: string[];
}

/** This computer, among the computers the filter takes. */
export const THIS_COMPUTER = "here";

const strings = (saved: unknown) =>
  Array.isArray(saved)
    ? saved.filter((s): s is string => typeof s === "string")
    : [];

export const filterCount = (f: ThreadFilter) =>
  f.projects.length + f.agents.length + f.computers.length;

/** The agents that worked in `chat`, most recent first. */
export const chatAgents = (chat: ChatSummary): AgentProvider[] =>
  chat.providers ?? (chat.provider ? [chat.provider] : []);

/** Every Scratchpad chat has a project of its own; the filter takes them as one. */
export const SCRATCHPAD = "scratchpad";

/** The filter without projects that are gone: one's id would hide everything, with nothing to untick. */
export const withoutRemoved = (
  f: ThreadFilter,
  projects: ReadonlyMap<string, unknown>,
): ThreadFilter => ({
  ...f,
  projects: f.projects.filter((id) => id === SCRATCHPAD || projects.has(id)),
});

/** `project` is a project's id, or SCRATCHPAD. */
export const takesProject = (f: ThreadFilter, project: string) =>
  !f.projects.length || f.projects.includes(project);

/** A paired computer's id, or THIS_COMPUTER. */
export const takesComputer = (f: ThreadFilter, computer: string) =>
  !f.computers.length || f.computers.includes(computer);

/** The computer a thread runs on: the one it's live on, or this one. */
const computerOf = (chat: ChatSummary) =>
  liveAway(chat) ? chat.sentTo!.computerId : THIS_COMPUTER;

export const passes = (f: ThreadFilter, project: string, chat: ChatSummary) =>
  takesProject(f, project) &&
  takesComputer(f, computerOf(chat)) &&
  (!f.agents.length || chatAgents(chat).some((a) => f.agents.includes(a)));

/**
 * One of a paired computer's own threads, which has a project here only
 * once a thread here follows it.
 */
export const passesThere = (
  f: ThreadFilter,
  computerId: string,
  thread: ComputerThread & { here?: { projectId: string } },
) =>
  takesComputer(f, computerId) &&
  takesProject(f, thread.here?.projectId ?? "") &&
  (!f.agents.length ||
    (!!thread.provider && f.agents.includes(thread.provider)));

/** The tree with only the projects `keep` takes, and the groups holding them. */
export function pruneTree(
  node: ProjectFolderNode,
  keep: (id: string) => boolean,
): ProjectFolderNode {
  return {
    ...node,
    folders: node.folders
      .map((f) => pruneTree(f, keep))
      .filter((f) => f.folders.length || f.projects.length),
    projects: node.projects.filter((p) => keep(p.id)),
  };
}

export type ThreadFilterState = ReturnType<typeof useThreadFilter>;

function parseFilter(saved: string | null): ThreadFilter {
  const s = (saved ? JSON.parse(saved) : {}) as Partial<
    Record<keyof ThreadFilter, unknown>
  >;
  return {
    projects: strings(s.projects),
    agents: strings(s.agents) as AgentProvider[],
    computers: strings(s.computers),
  };
}

/** One filter for every part of the sidebar that reads it, kept per device. */
const stored = persistedStore("relay-sidebar-filter", parseFilter, (f) =>
  JSON.stringify(f),
);

/** The sidebar's filter, kept per device. */
export function useThreadFilter() {
  const filter = stored.use();
  return {
    filter,
    toggle(kind: keyof ThreadFilter, id: string) {
      const f = stored.get();
      stored.set({
        ...f,
        [kind]: (f[kind] as string[]).includes(id)
          ? f[kind].filter((x) => x !== id)
          : [...f[kind], id],
      });
    },
    clear: () => stored.set({ projects: [], agents: [], computers: [] }),
  };
}
