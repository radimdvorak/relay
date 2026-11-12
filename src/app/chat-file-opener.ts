import type { QueryClient } from "@tanstack/react-query";
import {
  matchLink,
  splitOutsidePath,
  type ProjectFileLink,
} from "../../shared/project-file-links";
import type { Project } from "../../shared/projects";
import type { WorkingTree } from "../../shared/working-tree";
import { api } from "../lib/api";
import {
  changedTarget,
  fileTarget,
  type FilesRequest,
} from "../features/files/file-link-target";
import { chatElsewhere, type ChatElsewhere } from "./chat-elsewhere";

/** What opening a link in the folder `id` needs. */
interface Opening {
  id: string;
  qc: QueryClient;
  elsewhere: ChatElsewhere;
  openInEditor: (target: FilesRequest) => boolean;
  onError: (error: unknown) => void;
  isCurrent: () => boolean;
}

/**
 * A file clicked in the chat opens in Files, with its changes in place
 * when it has some (in its Review in a PR thread). A name that fits several
 * files lists them in Files. A file outside the project opens in Files too,
 * a folder outside it in the system's file manager. One the thread changed
 * in another repository opens with its changes, found by the name the agent
 * gave it. `startRequest` marks the click as the latest and says whether it
 * still is when an answer arrives.
 */
export function chatFileOpener({
  project,
  folder,
  chatId,
  inReview,
  qc,
  openInEditor,
  revealInReview,
  startRequest,
  onError,
}: {
  project?: Project;
  folder: { where: string; tree?: WorkingTree };
  chatId: string | null;
  /** The thread is a PR's, whose changes are its Review. */
  inReview: boolean;
  qc: QueryClient;
  openInEditor: (target: FilesRequest) => boolean;
  revealInReview: (target: ProjectFileLink) => void;
  startRequest: () => () => boolean;
  onError: (error: unknown) => void;
}) {
  const id = folder.where;
  const elsewhere = chatElsewhere(qc, chatId);
  return async function openChatFile(target: ProjectFileLink) {
    const isCurrent = startRequest();
    const opening = { id, qc, elsewhere, openInEditor, onError, isCurrent };
    if (target.outside && target.directory)
      return revealFolder(target.path, onError);
    if (target.outside) return openOutside(target, opening);
    if (inReview) return revealInReview(target);
    try {
      const changes = project!.plain
        ? []
        : (folder.tree ?? (await api.projectWorkingTree(id))).changes.map(
            (c) => c.path,
          );
      if (!isCurrent()) return;
      const changed = changedTarget(target, changes);
      if (changed)
        return void openInEditor({ ...changed, changes: !changed.directory });
      // An agent naming a folder elsewhere by its bare name gets a link here.
      if (target.directory) return await openFolder(target, opening);
      await openFile(target, opening);
    } catch (e) {
      if (isCurrent()) onError(e);
    }
  };
}

function revealFolder(path: string, onError: (error: unknown) => void) {
  const { folder, name } = splitOutsidePath(path);
  void api.revealProjectPath(folder, name).catch(onError);
}

/** A file by its absolute path; with its changes when the thread changed it in another repository. */
async function openOutside(target: ProjectFileLink, opening: Opening) {
  const changed = await opening.elsewhere.file(target).catch(() => undefined);
  if (opening.isCurrent()) opening.openInEditor(changed ?? target);
}

/** A folder of the project, or else another repository the thread changed, by its name. */
async function openFolder(target: ProjectFileLink, opening: Opening) {
  const { id, isCurrent } = opening;
  const inProject = await api.projectDirectory(id, target.path).then(
    () => true,
    () => false,
  );
  if (!isCurrent()) return;
  if (inProject) return void opening.openInEditor(target);
  const repository = await opening.elsewhere.folder(target);
  if (!isCurrent()) return;
  if (repository) return revealFolder(repository, opening.onError);
  opening.onError(new Error(`There's no ${target.path}/ in this project.`));
}

/** A file of the project, or else one the thread changed in another repository, or else a search for it. */
async function openFile(target: ProjectFileLink, opening: Opening) {
  const { id, isCurrent } = opening;
  const found = matchLink(
    target,
    await opening.qc.query({
      queryKey: ["project-files", id],
      queryFn: () => api.projectFiles(id),
      staleTime: 5000,
    }),
  );
  if (!isCurrent()) return;
  // Ignored files (an agent's output folder) aren't in Git's list but are on disk.
  const onDisk =
    !found.length &&
    (await api.projectFileInfo(id, target.path).then(
      () => true,
      () => false,
    ));
  const changedElsewhere =
    !found.length && !onDisk ? await opening.elsewhere.file(target) : undefined;
  if (isCurrent())
    opening.openInEditor(changedElsewhere ?? fileTarget(target, found, onDisk));
}
