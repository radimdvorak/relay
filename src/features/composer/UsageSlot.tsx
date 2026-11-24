import type { ReactNode } from "react";
import type { ThreadCost } from "../../../shared/thread-cost";
import { accountUsageName } from "../accounts/AccountSwitch";
import type { ThreadAccounts } from "../accounts/useThreadAccounts";
import { usageShownFor } from "../agents/provider-usage";
import { ThreadCostButton } from "./ThreadCost";
import { activeUsageModel, UsageRing } from "./UsageRing";
import type { AgentRuns } from "./useAgentRuns";
import type { ModelCatalogs } from "./useModelCatalogs";

/**
 * The toolbar's usage control: the limits that cap the picked model, and what
 * the thread has cost where the agent priced its answers. Neither stands in
 * for the other. OpenCode prices an answer from a model outside its Go plan,
 * and its limits can be unavailable, so the price stays whether or not a ring
 * shows.
 */
export function usageControl({
  provider,
  runs,
  catalogs,
  accounts,
  computer,
  cost,
}: {
  provider: string;
  runs: Pick<AgentRuns, "choiceFor" | "claudeRuns" | "claude">;
  catalogs: Pick<ModelCatalogs, "of" | "defaults">;
  accounts: ThreadAccounts;
  /** The paired computer the project lives on; left out for this one. */
  computer?: { id: string };
  cost?: ThreadCost;
}): ReactNode {
  const limits = usageShownFor(provider, computer);
  if (!limits && !cost) return null;
  const model = limits ? activeUsageModel(provider, runs, catalogs) : undefined;
  return (
    <>
      {limits && (
        <UsageRing
          provider={provider}
          modelId={model?.id}
          modelName={model?.name}
          account={accounts.of(provider)}
          accountName={accountUsageName(provider, accounts)}
        />
      )}
      {cost && <ThreadCostButton cost={cost} />}
    </>
  );
}
