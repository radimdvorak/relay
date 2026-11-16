import { useState } from "react";
import { KeyRound } from "lucide-react";
import type { AgentResponse } from "../../../shared/agent-modes";
import { errorMessage } from "../../../shared/error-message";
import {
  SECRET_FILE_LIFE_HOURS,
  SECRET_MAX_CHARS,
  type SecretRequest,
} from "../../../shared/secret-request";
import "./agent-request.css";

/**
 * The card an agent's request_secret leaves in its answer. What is typed
 * stays in this field until Save: no draft keeps it, and the thread only
 * learns that it was saved or declined.
 */
export function SecretRequestCard({
  secret,
  agent,
  onRespond,
}: {
  secret: SecretRequest;
  /** The asking agent's name, as the card says it. */
  agent: string;
  onRespond: (response: AgentResponse) => Promise<void>;
}) {
  const [value, setValue] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  async function respond(given: string | null) {
    setBusy(true);
    setError(undefined);
    try {
      await onRespond({ kind: "secret", value: given });
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }
  return (
    <section
      className="agent-request"
      aria-label={`${agent} asks for a secret: ${secret.name}`}
    >
      <header>
        <KeyRound size={15} />
        <strong>{secret.name}</strong>
      </header>
      {secret.reason && <p className="agent-request-note">{secret.reason}</p>}
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void respond(value.trim());
        }}
      >
        <fieldset disabled={busy}>
          {/* A password field: screen readers don't read it back, and it
              opts out of the password managers that fill saved logins. */}
          <input
            type="password"
            aria-label={secret.name}
            placeholder="Paste or type it…"
            maxLength={SECRET_MAX_CHARS}
            autoComplete="off"
            autoCapitalize="off"
            autoCorrect="off"
            spellCheck={false}
            data-1p-ignore
            data-lpignore="true"
            data-bwignore="true"
            data-form-type="other"
            value={value}
            onChange={(event) => setValue(event.target.value)}
          />
          <p className="agent-request-note">
            It goes to a private file that {agent}'s commands read, not into the
            chat. Relay deletes the file after {SECRET_FILE_LIFE_HOURS} hours.
          </p>
          <footer>
            <button
              type="button"
              title={`Tell ${agent} to go on without it`}
              onClick={() => void respond(null)}
            >
              Decline
            </button>
            <button type="submit" className="primary" disabled={!value.trim()}>
              Save
            </button>
          </footer>
        </fieldset>
      </form>
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
