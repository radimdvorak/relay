// usage_limits: each agent's plan limits on the accounts a thread uses.
import { pinnedAccount } from "../../shared/agent-accounts";
import { usageProviders, type UsageProvider } from "../../shared/agents";
import { resetsIn, type ProviderUsage } from "../../shared/provider-usage";
import type { ProjectChat } from "../../shared/projects";
import { localTime } from "./tool-output";

export type ReadUsage = (
  provider: UsageProvider,
  force: boolean,
  account?: string,
) => Promise<ProviderUsage>;

/** Each agent's limits on the accounts the calling thread uses. */
export function usageLimits(caller: ProjectChat, readUsage: ReadUsage) {
  const now = Date.now();
  return Promise.all(
    usageProviders.map(async (agent) => {
      const account = pinnedAccount(caller.accounts, agent);
      const usage = await readUsage(agent, false, account);
      return {
        agent,
        ...(account ? { account } : {}),
        ...Object.fromEntries(
          usage.windows.map((w) => [
            w.id ?? w.kind,
            {
              ...(w.label ? { label: w.label } : {}),
              usedPercent: Math.round(w.usedPercent),
              ...(w.resetsAt
                ? {
                    resetsAt: localTime(w.resetsAt),
                    resetsIn: resetsIn(w.resetsAt, now),
                  }
                : {}),
            },
          ]),
        ),
        ...(usage.message ? { note: usage.message } : {}),
      };
    }),
  );
}
