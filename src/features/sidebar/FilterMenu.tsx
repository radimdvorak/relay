import { ListFilter, Monitor } from "lucide-react";
import { agentName } from "../../../shared/agents";
import type { AgentProvider, Project } from "../../../shared/projects";
import { relativeDate } from "../../lib/relative-date";
import { SearchSelect } from "../../ui/SearchSelect";
import { ProviderIcon } from "../agents/ComposerModelPicker";
import { ProjectBadge } from "../projects/ProjectBadge";
import { DeviceIcon } from "../handoff/DeviceIcon";
import { usePairedComputers } from "../handoff/usePairedComputers";
import {
  filterCount,
  THIS_COMPUTER,
  type ThreadFilter,
  type ThreadFilterState,
} from "./useThreadFilter";

/**
 * Narrows the sidebar to some computers, projects or agents. Computers only
 * show up once one is paired, and agents once threads ran more than one,
 * since a single one filters nothing.
 */
export function FilterMenu({
  state: { toggle, clear },
  filter,
  projects,
  agents,
}: {
  state: ThreadFilterState;
  /** What's ticked, of what's still there. */
  filter: ThreadFilter;
  /**
   * The projects to offer, latest activity first; `key` is what the filter
   * keeps, `at` when a thread in it last changed, if one has.
   */
  projects: { key: string; project: Project; at?: number }[];
  agents: AgentProvider[];
}) {
  const count = filterCount(filter);
  const computers = useComputerOptions(filter.computers);
  return (
    <SearchSelect
      label="Filter"
      placeholder="Search computers, projects and agents…"
      className="sb-filter-popup"
      triggerClassName={`sb-top-button sb-filter ${count ? "active" : ""}`}
      triggerLabel={count ? `Filter, ${count} applied` : "Filter"}
      trigger={
        <>
          <ListFilter size={15} />
          {count > 0 && <span className="sb-bell-count">{count}</span>}
        </>
      }
      checked={[
        ...filter.computers.map((id) => `computer:${id}`),
        ...filter.projects.map((id) => `project:${id}`),
        ...filter.agents.map((a) => `agent:${a}`),
      ]}
      // Computers and agents first: a handful of them, over a list that may scroll.
      options={[
        ...computers,
        ...(agents.length > 1
          ? agents.map((a) => ({
              value: `agent:${a}`,
              label: agentName(a),
              icon: <ProviderIcon provider={a} />,
              group: "Agents",
            }))
          : []),
        ...projects.map(({ key, project, at }) => ({
          value: `project:${key}`,
          label: project.scratch ? "Scratchpad" : project.name,
          detail: at ? relativeDate(new Date(at).toISOString()) : undefined,
          icon: <ProjectBadge project={project} />,
          group: "Projects",
        })),
      ]}
      onChange={(value) => {
        const [kind, id] = value.split(/:(.*)/);
        toggle(kindOf[kind as keyof typeof kindOf], id);
      }}
      action={count ? { label: "Clear filter", onSelect: clear } : undefined}
    />
  );
}

/** What an option's value starts with, and the list in the filter it ticks. */
const kindOf = {
  computer: "computers",
  agent: "agents",
  project: "projects",
} as const;

/** This computer and each paired one, and one still ticked that isn't paired anymore. */
function useComputerOptions(ticked: string[]) {
  const paired = usePairedComputers().data ?? [];
  if (!paired.length && !ticked.length) return [];
  const names = new Map(paired.map((c) => [c.id, c.name]));
  const unpaired = ticked.filter(
    (id) => id !== THIS_COMPUTER && !names.has(id),
  );
  return [
    { id: THIS_COMPUTER, label: "This computer" },
    ...paired.map((c) => ({ id: c.id, label: c.name })),
    ...unpaired.map((id) => ({ id, label: "A computer no longer paired" })),
  ].map(({ id, label }) => ({
    value: `computer:${id}`,
    label,
    icon:
      id === THIS_COMPUTER ? (
        <Monitor size={14} />
      ) : (
        <DeviceIcon name={label} size={14} />
      ),
    group: "Computers",
  }));
}
