import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { SavedSecrets } from "../../../shared/secret-request";
import { api } from "../../lib/api";

const NONE: SavedSecrets = { secrets: [] };

/** A project, or the one a thread is in. */
type SecretsOf = { projectId: string } | { chatId: string };

/** Every list of saved secrets, whichever project or thread asked for it. */
const ALL_SAVED = ["project-secrets"];

/**
 * The secrets Relay remembers for a project, by name, and a way to say they
 * changed: an Always answer in a thread, or a Remove in settings. For a thread
 * or project on another computer they are that computer's, which `saved` names.
 */
export function useSavedSecrets(of: SecretsOf) {
  const qc = useQueryClient();
  const query = useQuery({
    queryKey: [...ALL_SAVED, of],
    queryFn: () =>
      "chatId" in of
        ? api.threadSecrets(of.chatId)
        : api.projectSecrets(of.projectId),
  });
  return {
    saved: query.data ?? NONE,
    /** Why the list couldn't be read: its computer is offline, or runs an older Relay. */
    error: query.error ?? undefined,
    changed: () => qc.invalidateQueries({ queryKey: ALL_SAVED }),
  };
}
