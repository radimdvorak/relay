import type { ReactNode } from "react";
import {
  agentName,
  reportsUsage,
  type AgentProvider,
  type UsageProvider,
} from "../../../shared/agents";
import type { Category, MessageProvider } from "./model-picker-catalog";
import { usageShownFor } from "./provider-usage";
import { ModelReload, reloadsModels } from "./ModelReload";
import { NoUsageStrip, UsageStrip } from "./UsageStrip";
import type { usePickerUsage } from "./usePickerUsage";

export type PickerAccount = {
  of: (provider: UsageProvider) => string | undefined;
  footer: (
    provider: UsageProvider,
    now: number,
    container?: HTMLElement,
  ) => ReactNode;
};

/**
 * Where an agent's limits aren't shown. An agent with limits this computer
 * can't read says so on every tab, beside its reload button; one with none
 * keeps its own tab for the reload button.
 */
function NoUsageNote({
  category,
  agent,
}: {
  category: Category;
  agent: AgentProvider;
}) {
  const name = agentName(agent);
  if (reportsUsage(agent)) {
    return <NoUsageStrip name={name} status="Not read from other computers" />;
  }
  if (category === agent && reloadsModels(agent)) return null;
  return <NoUsageStrip name={name} />;
}

/** Under the list: the agent's usage, or how to reload its models. */
export function ModelPickerFooter({
  category,
  provider,
  computer,
  account,
  usage: { usage, now },
  container,
}: {
  category: Category;
  /** The thread's agent. */
  provider: MessageProvider;
  computer?: { id: string; name: string };
  account?: PickerAccount;
  usage: ReturnType<typeof usePickerUsage>;
  container?: HTMLElement;
}) {
  // Favorites show the thread's agent, so every tab keeps the same footer.
  const footerAgent = category === "favorites" ? provider : category;
  return (
    <>
      {category !== "favorites" &&
        category !== "message" &&
        reloadsModels(category) && (
          <ModelReload key={category} provider={category} computer={computer} />
        )}
      {usageShownFor(footerAgent, computer) ? (
        (account?.footer(footerAgent, now, container) ?? (
          <UsageStrip
            usage={usage[footerAgent]}
            now={now}
            accountId={account?.of(footerAgent)}
            container={container}
          />
        ))
      ) : footerAgent !== "message" ? (
        <NoUsageNote category={category} agent={footerAgent} />
      ) : null}
      {category === "message" && (
        <p className="model-picker-note">
          Send a message without running an agent.
        </p>
      )}
    </>
  );
}
