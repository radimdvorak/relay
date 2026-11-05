import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  linkedFoldersSchema,
  linkName,
  threadLinks,
  tildePath,
  type ChatSummary,
  type LinkedFolder,
  type Project,
} from "../../../shared/projects";
import { threadStorage } from "../../lib/thread-storage";
import { api } from "../../lib/api";
import { useAddDir } from "./useAddDir";

export type ThreadLinks = ReturnType<typeof useThreadLinks>;

/**
 * The folders a thread reaches beyond its project: the project's links and
 * its own. An unsent thread holds its own until its first write makes it;
 * `/add-dir` links one, completing paths as they're typed.
 */
export function useThreadLinks({
  project,
  chat,
  draftId,
  onError,
}: {
  project: Project;
  chat?: ChatSummary;
  draftId: string;
  onError: (error: unknown) => void;
}) {
  const qc = useQueryClient();
  const [draft, setDraft] = useState(() => ({
    id: draftId,
    links: threadStorage(draftId).links.load(),
  }));
  // Shown at once; the thread list catches up after the save.
  const [pending, setPending] = useState<{
    id: string;
    links: LinkedFolder[];
  }>();
  useEffect(() => setPending(undefined), [chat?.links]);
  const own = chat
    ? pending?.id === chat.id
      ? pending.links
      : (chat.links ?? [])
    : draft?.id === draftId
      ? draft.links
      : [];
  const current = useRef(own);
  current.current = own;
  const queue = useRef(Promise.resolve());
  function enqueue<T>(work: () => Promise<T>): Promise<T> {
    const next = queue.current.then(work);
    queue.current = next.then(
      () => {},
      () => {},
    );
    return next;
  }
  const links = threadLinks(project.settings?.links, own, project.path);

  async function save(next: LinkedFolder[]) {
    next = linkedFoldersSchema.parse(next);
    if (!chat) {
      threadStorage(draftId).links.save(next);
      current.current = next;
      setDraft({ id: draftId, links: next });
      return;
    }
    setPending({ id: chat.id, links: next });
    try {
      const saved = await api.setProjectChatLinks(chat.id, next);
      current.current = saved.links ?? [];
      qc.setQueriesData<ChatSummary[]>(
        { queryKey: ["project-chats"] },
        (list) => list?.map((c) => (c.id === saved.id ? saved : c)),
      );
    } catch (e) {
      setPending(undefined);
      onError(e);
    }
  }

  /** Links a folder to this thread, read only; an error says why it can't. */
  async function link(path: string): Promise<string | undefined> {
    const info = await api.inspectFolder(path);
    if (info.kind === "missing") return `${tildePath(info.path)} isn't there.`;
    if (info.kind === "file")
      return `${linkName(info.path)} is a file, not a folder.`;
    if (info.path === project.path)
      return `That's ${project.name}'s own folder.`;
    if (
      threadLinks(project.settings?.links, current.current, project.path).some(
        (l) => l.path === info.path,
      )
    )
      return `${linkName(info.path)} is linked already.`;
    await save([...current.current, { path: info.path, access: "read" }]);
  }

  const linkFolder = (path: string) => enqueue(() => link(path));
  const { options, command, listed } = useAddDir({
    project,
    links,
    link: linkFolder,
    onError,
  });

  return {
    links,
    /** Goes up as each listing `/add-dir` asked for arrives. */
    listed,
    /** What the unsent thread is made with. */
    draftLinks: chat ? undefined : own,
    link: linkFolder,
    async change(path: string, next: Partial<LinkedFolder>) {
      await enqueue(() =>
        save(
          current.current.map((l) => (l.path === path ? { ...l, ...next } : l)),
        ),
      );
    },
    async unlink(path: string) {
      await enqueue(() => save(current.current.filter((l) => l.path !== path)));
    },
    /** Moves a thread's link to its project, so every thread reaches it. */
    linkToProject: (path: string) =>
      enqueue(async () => {
        const moving = current.current.find((l) => l.path === path);
        if (!moving) return;
        try {
          let saved: Project;
          if (chat) {
            const result = await api.promoteProjectChatLink(chat.id, path);
            saved = result.project;
            current.current = result.chat.links ?? [];
            setPending({ id: chat.id, links: result.chat.links ?? [] });
            qc.setQueriesData<ChatSummary[]>(
              { queryKey: ["project-chats"] },
              (list) =>
                list?.map((c) => (c.id === result.chat.id ? result.chat : c)),
            );
          } else {
            saved = await api.addProjectLinks(project.id, [moving]);
            await save(current.current.filter((l) => l.path !== path));
          }
          qc.setQueriesData<Project[]>({ queryKey: ["projects"] }, (list) =>
            list?.map((p) =>
              p.id === project.id ? { ...p, settings: saved.settings } : p,
            ),
          );
        } catch (e) {
          onError(e);
        }
      }),
    /** `/add-dir`, typed or picked from its menu. */
    command,
    /** What `/add-dir` offers for what's typed after it. */
    options,
  };
}
