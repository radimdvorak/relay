import { useState } from "react";
import { KeyRound } from "lucide-react";
import type { AgentResponse } from "../../../shared/agent-modes";
import { errorMessage } from "../../../shared/error-message";
import {
  alwaysExplained,
  SECRET_MAX_CHARS,
  secretDestination,
  type SecretRequest,
} from "../../../shared/secret-request";
import { useSavedSecrets } from "./useSavedSecrets";
import "./secrets.css";

/**
 * The card an agent's request_secret leaves in its answer; the thread styles
 * it as one of its request cards. What is typed stays in this field until it
 * is saved: no draft keeps it, and the thread only learns how it ended.
 */
export function SecretRequestCard({
  secret,
  chatId,
  agent,
  onRespond,
}: {
  secret: SecretRequest;
  /** The thread it was asked in; Always remembers the secret for its project. */
  chatId: string;
  /** The asking agent's name, as the card says it. */
  agent: string;
  onRespond: (response: AgentResponse) => Promise<void>;
}) {
  const [value, setValue] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const { saved, changed } = useSavedSecrets({ chatId });
  const typed = value.trim();
  async function respond(given: string | null, always = false) {
    setBusy(true);
    setError(undefined);
    try {
      await onRespond({
        kind: "secret",
        value: given,
        ...(always ? { remember: true as const } : {}),
      });
      if (always) void changed();
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
          void respond(typed);
        }}
      >
        <fieldset disabled={busy}>
          <SecretField name={secret.name} value={value} onChange={setValue} />
          <p className="agent-request-note">{secretDestination(agent)}</p>
          <p className="agent-request-note">
            {alwaysExplained(secret.name, saved)}
          </p>
          <SecretActions
            agent={agent}
            canSave={!!typed}
            onDecline={() => void respond(null)}
            onAlways={() => void respond(typed, true)}
          />
        </fieldset>
      </form>
      {error && <p role="alert">{error}</p>}
    </section>
  );
}

/**
 * A password field: screen readers don't read it back, and it opts out of the
 * password managers that fill saved logins.
 */
function SecretField({
  name,
  value,
  onChange,
}: {
  name: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <input
      type="password"
      aria-label={name}
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
      onChange={(event) => onChange(event.target.value)}
    />
  );
}

/** Decline, Always, then Save, weighted like the approval tab's choices; Save submits the form. */
function SecretActions({
  agent,
  canSave,
  onDecline,
  onAlways,
}: {
  agent: string;
  canSave: boolean;
  onDecline: () => void;
  onAlways: () => void;
}) {
  return (
    <footer className="secret-request-actions">
      <button
        type="button"
        title={`Tell ${agent} to go on without it`}
        onClick={onDecline}
      >
        Decline
      </button>
      <button
        type="button"
        disabled={!canSave}
        title="Save it and don't ask again in this project"
        onClick={onAlways}
      >
        Always
      </button>
      <button type="submit" className="primary" disabled={!canSave}>
        Save
      </button>
    </footer>
  );
}
