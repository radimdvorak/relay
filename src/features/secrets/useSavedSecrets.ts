import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { SavedSecrets, SecretsOf } from "../../../shared/secret-request";
import { api } from "../../lib/api";

const NONE: SavedSecrets = { secrets: [] };

/** Every list of saved secrets, whichever project or thread asked for it. */
const ALL_SAVED = ["project-secrets"];

/**
 * The secrets Relay remembers for a project, by name, and a way to say they
 * changed: an Always answer in a thread, or a Remove in settings.
 */
export function useSavedSecrets(of: SecretsOf) {
  const qc = useQueryClient();
  const query = useQuery({
    queryKey: [...ALL_SAVED, of],
    queryFn: () => api.projectSecrets(of),
  });
  return {
    saved: query.data ?? NONE,
    changed: () => qc.invalidateQueries({ queryKey: ALL_SAVED }),
  };
}
