import {
  accountIdSchema,
  accountLabelSchema,
  accountProviderSchema,
} from "../../shared/agent-accounts";
import { z } from "zod";
import { idSchema } from "../../shared/validation";
import { takes, type ApiContext, type Handlers } from "./context";

/** Google's codes, or the whole page address they're on, which the CLI also takes. */
const signInCodeSchema = z.string().trim().min(1).max(4000);

/** Agent accounts: Settings' list, and a thread's own pick. */
export function accountHandlers(ctx: ApiContext) {
  const { agentAccounts: accounts, projectChats } = ctx;
  return {
    agentAccounts: () => accounts.state(),
    addAgentAccount: takes(
      [accountProviderSchema, accountLabelSchema],
      (provider, label) => accounts.add(provider, label),
    ),
    signInAgentAccount: takes(
      [accountProviderSchema, accountIdSchema],
      (provider, id) => accounts.signInAgain(provider, id),
    ),
    cancelAgentAccountSignIn: async () => accounts.cancelSignIn(),
    enterAgentAccountSignInCode: takes([signInCodeSchema], async (code) =>
      accounts.enterSignInCode(code),
    ),
    renameAgentAccount: takes(
      [accountProviderSchema, accountIdSchema, accountLabelSchema],
      (provider, id, label) => accounts.rename(provider, id, label),
    ),
    removeAgentAccount: takes(
      [accountProviderSchema, accountIdSchema],
      (provider, id) => accounts.remove(provider, id),
    ),
    useAgentAccount: takes(
      [accountProviderSchema, accountIdSchema],
      (provider, id) => accounts.use(provider, id),
    ),
    setThreadAccount: takes(
      [idSchema, accountProviderSchema, accountIdSchema],
      (chatId, provider, id) => projectChats.setAccount(chatId, provider, id),
    ),
  } satisfies Handlers;
}
