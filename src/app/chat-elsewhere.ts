import type { QueryClient } from "@tanstack/react-query";
import type { ProjectFileLink } from "../../shared/project-file-links";
import { api } from "../lib/api";
import {
  elsewhereFileOf,
  elsewhereFolderOf,
} from "../features/changes/elsewhere/elsewhere-links";
import { elsewhereKey } from "../features/changes/elsewhere/useElsewhere";
import { elsewhereChange } from "../features/files/change-opens";
import type { FilesRequest } from "../features/files/file-link-target";

export interface ChatElsewhere {
  /** The file the thread changed in another repository that a link names, with its changes in place. */
  file(target: ProjectFileLink): Promise<FilesRequest | undefined>;
  /** The top folder of another repository the thread changed that a link names. */
  folder(target: ProjectFileLink): Promise<string | undefined>;
}

/**
 * What a chat link finds among the files `chatId` and the threads it started
 * changed in other repositories. A draft has changed nothing yet, and a
 * failed read counts as nothing changed.
 */
export function chatElsewhere(
  qc: QueryClient,
  chatId: string | null,
): ChatElsewhere {
  async function groups() {
    if (!chatId) return [];
    const changes = await qc
      .query({
        queryKey: elsewhereKey(chatId),
        queryFn: () => api.threadElsewhere(chatId),
        staleTime: 5000,
      })
      .catch(() => undefined);
    return changes?.groups ?? [];
  }

  return {
    async file(target) {
      const pick = elsewhereFileOf(target, await groups());
      return pick && elsewhereChange(chatId!, pick);
    },
    async folder(target) {
      return elsewhereFolderOf(target, await groups());
    },
  };
}
