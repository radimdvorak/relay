import type { ReactNode } from "react";
import type { ThreadCost } from "../../../shared/thread-cost";
import { accountUsageName } from "../accounts/AccountSwitch";
import type { ThreadAccounts } from "../accounts/useThreadAccounts";
import { usageShownFor } from "../agents/provider-usage";
import { ThreadCostButton } from "./ThreadCost";
import { UsageRing } from "./UsageRing";

/**
 * The toolbar's usage control: the agent's plan limits, and what the thread
 * has cost where the agent priced its answers. Neither stands in for the
 * other. OpenCode prices an answer from a model outside its Go plan, and its
 * limits can be unavailable, so the price stays whether or not a ring shows.
 */
export function usageControl({
  provider,
  accounts,
  computer,
  cost,
}: {
  provider: string;
  accounts: ThreadAccounts;
  /** The paired computer the project lives on; left out for this one. */
  computer?: { id: string };
  cost?: ThreadCost;
}): ReactNode {
  const limits = usageShownFor(provider, computer);
  if (!limits && !cost) return null;
  return (
    <>
      {limits && (
        <UsageRing
          provider={provider}
          account={accounts.of(provider)}
          accountName={accountUsageName(provider, accounts)}
        />
      )}
      {cost && <ThreadCostButton cost={cost} />}
    </>
  );
}
