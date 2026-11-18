import { useState } from "react";
import { useProjectList } from "../../lib/workspace-queries";

/**
 * Which project's checkout the tools are read from: the one you picked, else
 * the one you came from, else the first project. "" is the personal
 * configuration.
 */
export function useToolsProject(initialProjectId: string | undefined) {
  const projects = useProjectList();
  const [selectedProject, setSelectedProject] = useState<string>();
  const offeredProjects =
    projects.data?.filter((p) => !p.scratch && !p.removed) ?? [];
  const requested =
    selectedProject ?? initialProjectId ?? offeredProjects[0]?.id ?? "";
  const projectId = offeredProjects.some((p) => p.id === requested)
    ? requested
    : "";
  const projectOptions = [
    ...offeredProjects.map((p) => ({ value: p.id, label: p.name })),
    { value: "", label: "Personal configuration", short: "Personal" },
  ];
  return {
    projects,
    projectName: offeredProjects.find((p) => p.id === projectId)?.name,
    projectId,
    projectOptions,
    selectProject: setSelectedProject,
  };
}
