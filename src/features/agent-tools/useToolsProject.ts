import { useState } from "react";
import { goneFolder } from "../../../shared/projects";
import { useProjectList } from "../../lib/workspace-queries";

/**
 * Which project's checkout the tools are read from: the one you picked, else
 * the one you came from, else the first project whose folder is still there.
 * "" is the personal configuration.
 */
export function useToolsProject(initialProjectId: string | undefined) {
  const projects = useProjectList();
  const [selectedProject, setSelectedProject] = useState<string>();
  /** Projects whose folder a read found moved or deleted. */
  const [gone, setGone] = useState<readonly string[]>([]);
  const offeredProjects =
    projects.data?.filter((p) => !p.scratch && !p.removed) ?? [];
  const candidates = [initialProjectId, ...offeredProjects.map((p) => p.id)];
  const requested = selectedProject ?? defaultProject(candidates, gone);
  const projectId = offeredProjects.some((p) => p.id === requested)
    ? requested
    : "";
  const projectOptions = [
    ...offeredProjects.map((p) => ({
      value: p.id,
      label: p.name,
      description: gone.includes(p.id) ? "Folder is gone" : undefined,
    })),
    { value: "", label: "Personal configuration", short: "Personal" },
  ];
  /** Remembers the project as gone when `read` finds its folder missing. */
  const readingProject = <T>(read: Promise<T>) =>
    read.catch((error: unknown) => {
      if (projectId && goneFolder(error))
        setGone((ids) => (ids.includes(projectId) ? ids : [...ids, projectId]));
      throw error;
    });
  return {
    projects,
    projectName: offeredProjects.find((p) => p.id === projectId)?.name,
    projectId,
    projectOptions,
    selectProject: setSelectedProject,
    readingProject,
  };
}

/** The first candidate not known to be gone; else the personal configuration. */
function defaultProject(
  candidates: readonly (string | undefined)[],
  gone: readonly string[],
): string {
  return candidates.find((id) => id && !gone.includes(id)) ?? "";
}
