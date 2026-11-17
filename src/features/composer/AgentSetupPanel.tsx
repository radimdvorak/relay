import { useState, type ReactNode } from "react";
import { Download } from "lucide-react";
import {
  agentInfo,
  agentName,
  type AgentProvider,
} from "../../../shared/agents";
import { openAgentSettings, openSettings } from "../../lib/settings-page";
import { Spinner } from "../../ui/ui";
import { InstallLog, plainOutput } from "../../ui/InstallLog";
import type { SignInPlace } from "../sign-in/useAgentSignIn";
import { AgentSignInPanel } from "./AgentSignInPanel";
import { ProviderIcon } from "../agents/ComposerModelPicker";
import "./agent-setup.css";

/** Why an agent can't run where the project does yet. */
export type AgentSetup =
  /** The project's computer was paired again under a new identity, or removed. */
  | { kind: "unpaired" }
  | { kind: "unreachable" }
  | { kind: "missing"; reason: string; installable: boolean }
  | { kind: "installing"; queued: boolean; output?: string }
  | { kind: "failed"; message: string; output?: string }
  | { kind: "signed-out" };

export type Computer = { id: string; name: string };

const lastLine = (output?: string) =>
  output && plainOutput(output).split("\n").filter(Boolean).at(-1);

export interface SetupActions {
  install: (p: AgentProvider) => Promise<void>;
  checkAgain: (p: AgentProvider) => Promise<void>;
  /** Asks again, so one signed in some other way meanwhile isn't sent to the terminal. */
  stillSignedOut: (p: AgentProvider) => Promise<boolean>;
  /** Its sign-in is under way: looks every few seconds for a while. */
  watch: (p: AgentProvider) => void;
}

export function AgentSetupPanel({
  provider,
  setup,
  computer,
  place,
  watching,
  actions,
  close,
}: {
  provider: AgentProvider;
  setup: AgentSetup;
  computer?: Computer;
  place: SignInPlace;
  watching: boolean;
  actions: SetupActions;
  /** Closes the picker, so the terminal the sign-in went to shows. */
  close: () => void;
}) {
  const [checking, setChecking] = useState(false);
  const { cli } = agentInfo(provider);
  const on = computer ? `on ${computer.name}` : "on this computer";
  const checkAgain = (
    <button
      type="button"
      className="text-button"
      disabled={checking}
      onClick={() => {
        setChecking(true);
        void actions.checkAgain(provider).finally(() => setChecking(false));
      }}
    >
      {checking ? "Checking…" : "Check again"}
    </button>
  );
  const glyph = (
    <span className="agent-setup-glyph">
      <ProviderIcon provider={provider} />
    </span>
  );

  switch (setup.kind) {
    case "unpaired":
      return (
        <div className="agent-setup">
          <h3>{computer!.name} isn't paired anymore</h3>
          <p>
            So this project can't run there. Pair it again, or add the project
            from where it is now.
          </p>
          <div className="agent-setup-actions">
            <button
              type="button"
              className="primary"
              onClick={() => {
                close();
                openSettings("computers");
              }}
            >
              Computers
            </button>
          </div>
        </div>
      );
    case "unreachable":
      return (
        <div className="agent-setup">
          <h3>Couldn't reach {computer!.name}</h3>
          <p>
            Relay couldn't ask it which agents it has. It may be asleep or
            offline.
          </p>
          <div className="agent-setup-actions">{checkAgain}</div>
        </div>
      );
    case "missing":
      return (
        <MissingAgent
          provider={provider}
          setup={setup}
          computer={computer}
          actions={actions}
          close={close}
          glyph={glyph}
          checkAgain={checkAgain}
        />
      );
    case "installing": {
      const line = lastLine(setup.output);
      return (
        <div className="agent-setup">
          {glyph}
          <h3>
            Installing {cli} {on}
          </h3>
          <p className="agent-setup-progress" role="status">
            <Spinner size={12} steady />
            <span>
              {setup.queued
                ? "Waiting for another agent's install to finish"
                : (line ?? "Starting…")}
            </span>
          </p>
          {setup.output && (
            <div className="agent-setup-actions">
              <span className="agent-setup-log">
                <InstallLog output={setup.output} />
              </span>
            </div>
          )}
        </div>
      );
    }
    case "failed":
      return (
        <div className="agent-setup">
          {glyph}
          <h3>
            Couldn't install {cli} {on}
          </h3>
          <p>{setup.message}</p>
          <div className="agent-setup-actions">
            <button
              type="button"
              className="primary"
              onClick={() => void actions.install(provider)}
            >
              Try again
            </button>
            {setup.output && (
              <span className="agent-setup-log">
                <InstallLog output={setup.output} />
              </span>
            )}
          </div>
        </div>
      );
    case "signed-out":
      return (
        <AgentSignInPanel
          provider={provider}
          computer={computer}
          place={place}
          checkAgain={watching && checkAgain}
          stillSignedOut={() => actions.stillSignedOut(provider)}
          onStarted={() => actions.watch(provider)}
          close={close}
        />
      );
  }
}

/** An agent that isn't on the computer, or can't run there: install it or set it up. */
function MissingAgent({
  provider,
  setup,
  computer,
  actions,
  close,
  glyph,
  checkAgain,
}: {
  provider: AgentProvider;
  setup: Extract<AgentSetup, { kind: "missing" }>;
  computer?: Computer;
  actions: SetupActions;
  close: () => void;
  glyph: ReactNode;
  checkAgain: ReactNode;
}) {
  const { cli } = agentInfo(provider);
  const name = agentName(provider);
  const on = computer ? `on ${computer.name}` : "on this computer";
  return (
    <div className="agent-setup">
      {glyph}
      <h3>
        {setup.installable ? `${cli} isn't ${on}` : `${name} can't run ${on}`}
      </h3>
      <p>
        {setup.installable
          ? `Install it to run ${name} threads here. Relay${computer ? ` on ${computer.name}` : ""} puts it in its own folder.`
          : setup.reason}
      </p>
      <div className="agent-setup-actions">
        {setup.installable ? (
          <button
            type="button"
            className="primary"
            onClick={() => void actions.install(provider)}
          >
            <Download size={14} aria-hidden />
            {computer ? `Install on ${computer.name}` : "Install"}
          </button>
        ) : (
          !computer && (
            <button
              type="button"
              className="primary"
              onClick={() => {
                close();
                openAgentSettings(provider);
              }}
            >
              Set up in Settings
            </button>
          )
        )}
        {checkAgain}
      </div>
    </div>
  );
}
