import { usageProviders, type UsageProvider } from "../../../shared/agents";
import type { ProviderUsage } from "../../../shared/provider-usage";
import { useNow } from "../../lib/useNow";
import { usageShownFor, useUsageReadings } from "./provider-usage";

/**
 * The picker's account-scoped readings; opening it observes the same cache as
 * the composer. Agents whose limits aren't the project computer's aren't read.
 */
export function usePickerUsage(
  open: boolean,
  accountOf?: (provider: UsageProvider) => string | undefined,
  computer?: { id: string },
) {
  const providers = usageProviders.filter((provider) =>
    usageShownFor(provider, computer),
  );
  const { readings } = useUsageReadings(
    providers.map((provider) => ({
      provider,
      account: accountOf?.(provider),
    })),
    open,
  );
  const usage = Object.fromEntries(
    providers.map((provider, i) => [provider, readings[i].data]),
  ) as Partial<Record<UsageProvider, ProviderUsage>>;
  const now = useNow(20_000, open);
  return { usage, now };
}
