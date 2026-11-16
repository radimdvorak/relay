// Saved secrets in project settings: what the project's agents get without
// asking, because a request for it was answered with Always. Names only.
import { useState } from "react";
import { KeyRound } from "lucide-react";
import type { Project } from "../../../shared/projects";
import { errorMessage } from "../../../shared/error-message";
import { api } from "../../lib/api";
import { useSavedSecrets } from "./useSavedSecrets";
import "./secrets.css";

const day = new Intl.DateTimeFormat(undefined, { dateStyle: "medium" });

export function SavedSecretsSetting({ project }: { project: Project }) {
  const { saved, changed } = useSavedSecrets({ projectId: project.id });
  const [error, setError] = useState<string>();
  async function remove(name: string) {
    setError(undefined);
    try {
      await api.forgetProjectSecret(project.id, name);
      await changed();
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  if (!saved.length)
    return (
      <p className="saved-secrets-none">
        None saved. Answer an agent's request with Always to save one.
      </p>
    );
  return (
    <>
      <ul className="saved-secrets" aria-label="Saved secrets">
        {saved.map((secret) => (
          <li key={secret.name}>
            <KeyRound size={14} aria-hidden />
            <span className="saved-secret-name">{secret.name}</span>
            <span className="saved-secret-when">
              {secret.sessionOnly
                ? "Until Relay quits"
                : `Saved ${day.format(secret.saved)}`}
            </span>
            <button
              type="button"
              className="text-button"
              aria-label={`Remove ${secret.name}`}
              onClick={() => void remove(secret.name)}
            >
              Remove
            </button>
          </li>
        ))}
      </ul>
      {error && <p role="alert">{error}</p>}
    </>
  );
}
