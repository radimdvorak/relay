import type { RunImportOffer } from "../import-offer";
import type { AddingJob, AddingStart, GithubRepos, NewProject } from "./adding";
import type {
  ImportApp,
  ImportProject,
  ImportChat,
  ImportSelection,
  ProjectImportResult,
} from "../project-import";
import type {
  FolderEntry,
  FolderInfo,
  FolderPreview,
  LinkSuggestion,
} from "./folders";
import type { Project } from "./project";
import type { ChatSummary } from "./threads";

/** Folders on disk, to add as projects or link to one. */
export interface ProjectFoldersApi {
  importProjects(app: ImportApp): Promise<ImportProject[]>;
  importProjectChats(
    app: ImportApp,
    key: string,
    folder?: string,
  ): Promise<ImportChat[]>;
  importSelectedProjects(
    app: ImportApp,
    selections: ImportSelection[],
  ): Promise<ProjectImportResult>;
  /** A Scratchpad thread opened to import `app`'s history with an agent, which gets the import tools. */
  startImportThread(
    app: ImportApp,
  ): Promise<{ project: Project; chat: ChatSummary }>;
  /** Imports what the user ticked in the list an agent offered (offer_import), and places the new projects. */
  runImportOffer(
    chatId: string,
    messageId: string,
    input: RunImportOffer,
  ): Promise<ProjectImportResult>;
  inspectFolder(path: string): Promise<FolderInfo>;
  /** The folders inside `dir`, for typing a path; `~` stands for home. */
  listFolders(dir: string): Promise<FolderEntry[]>;
  folderPreview(dir: string, computerId?: string): Promise<FolderPreview>;
  /** Finder's folder picker; null when cancelled. Adds nothing. */
  chooseFolder(title: string): Promise<string | null>;
  /** Folders worth linking to the project: the repositories beside it, then the other projects. */
  linkSuggestions(projectId: string): Promise<LinkSuggestion[]>;
  /** What the add-project palette opens on. */
  addingStart(computerId?: string): Promise<AddingStart>;
  /** Adds the folder at `path`; `setUpGit` runs `git init` in a plain one first. */
  addProjectAt(path: string, setUpGit?: boolean): Promise<Project>;
  /** Clones `remote` into a folder of its name inside `into`, then adds it. */
  cloneProject(remote: string, into: string): Promise<Project>;
  /** Makes the folder, with git and a GitHub repository when asked, then adds it. */
  createProject(spec: NewProject, computerId?: string): Promise<Project>;
  /** Stops the clone or creation under way; it rejects. */
  cancelProjectAdding(computerId?: string): Promise<void>;
  /** The repositories the `gh` login can see. */
  githubRepos(computerId?: string): Promise<GithubRepos>;
  onProjectAdding(callback: (job: AddingJob | null) => void): () => void;
}
