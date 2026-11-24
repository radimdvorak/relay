import type { AccountProvider, AgentAccountsState } from "./agent-accounts";
import type { ChatSummary } from "./projects";

/** Agent accounts: Settings' list, sign-ins, and a thread's own pick. */
export interface AgentAccountsApi {
  /** Claude Code and Codex accounts; see shared/agent-accounts. */
  agentAccounts(): Promise<AgentAccountsState>;
  /** Opens the CLI's browser sign-in for a new account; it's listed once that finishes. */
  addAgentAccount(provider: AccountProvider, label: string): Promise<void>;
  signInAgentAccount(provider: AccountProvider, id: string): Promise<void>;
  cancelAgentAccountSignIn(): Promise<void>;
  /** The code Google showed, for a sign-in waiting on one. */
  enterAgentAccountSignInCode(code: string): Promise<void>;
  renameAgentAccount(
    provider: AccountProvider,
    id: string,
    label: string,
  ): Promise<void>;
  removeAgentAccount(provider: AccountProvider, id: string): Promise<void>;
  /** The account new threads start on. */
  useAgentAccount(provider: AccountProvider, id: string): Promise<void>;
  /** Runs the thread's `provider` on another account from its next turn. */
  setThreadAccount(
    chatId: string,
    provider: AccountProvider,
    id: string,
  ): Promise<ChatSummary>;
}
