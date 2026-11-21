import { memo } from "react";
import { Popover } from "@base-ui/react/popover";
import { usePinOnClick } from "../../lib/usePinOnClick";
import { Flame, RefreshCw } from "lucide-react";
import {
  dialRings,
  metersForModel,
  paceGap,
  presentWindow,
  worstPace,
  type DialRingRole,
  type MeterPace,
  type ProviderUsage,
  type UsageMeter,
  type UsageModel,
} from "../../../shared/provider-usage";
import { putUsage, useUsageReadings } from "../agents/provider-usage";
import { ResetUsage } from "../agents/saved-resets/ResetUsage";
import { CreditsRow } from "../agents/UsageMeters";
import { SYSTEM_ACCOUNT } from "../../../shared/agent-accounts";
import { useQueryClient } from "@tanstack/react-query";
import { useNow } from "../../lib/useNow";
import { UsageDial } from "../agents/UsageDial";
import "../agents/composer-model-picker.css";
import {
  agentName,
  reportsUsage,
  type UsageProvider,
} from "../../../shared/agents";
import type { Recipient } from "../../../shared/recipient";
import type { ThreadCost } from "../../../shared/thread-cost";
import { accountUsageName } from "../accounts/AccountSwitch";
import type { ThreadAccounts } from "../accounts/useThreadAccounts";
import { ThreadCostButton } from "./ThreadCost";
import type { AgentRuns } from "./useAgentRuns";
import type { ModelCatalogs } from "./useModelCatalogs";

export type RingMeter = UsageMeter & {
  /** True when this window caps the currently picked model. */
  active: boolean;
  /** Ring colour slot when drawn on the dial; undefined falls back to kind. */
  ringRole?: DialRingRole;
};

export type RingState = {
  /** Every reported window for the popover, active pool(s) first. */
  meters: RingMeter[];
  /** Only the windows that cap the picked model, drawn on the dial. */
  activeMeters: UsageMeter[];
  pace: MeterPace;
  label: string;
};

/**
 * Windows as popover rows (active pool first) plus the active dial meters,
 * worst active pace and screen-reader summary.
 */
export function ringState(
  usage: ProviderUsage | undefined,
  now: number,
  model?: UsageModel,
): RingState | null {
  if (!usage?.windows.length) return null;
  const presented = usage.windows.map((window) =>
    presentWindow(window, now, usage.activeHours),
  );
  const activeMeters = metersForModel(usage.provider, presented, model);
  const activeSet = new Set(activeMeters);
  const roles = new Map(
    dialRings(activeMeters).map((entry) => [entry.meter, entry.role]),
  );
  const toRow = (meter: UsageMeter): RingMeter => ({
    ...meter,
    active: activeSet.has(meter),
    ringRole: roles.get(meter),
  });
  const meters = [
    ...presented.filter((m) => activeSet.has(m)).map(toRow),
    ...presented.filter((m) => !activeSet.has(m)).map(toRow),
  ];
  const pace = worstPace(activeMeters);
  const label = activeMeters
    .map((m) =>
      [`${m.label} ${m.leftPercent}% left`, m.limitLabel, m.resetLabel]
        .filter(Boolean)
        .join(", "),
    )
    .join("; ");
  return { meters, activeMeters, pace, label };
}

/** The model `provider` will actually run from this composer, if known. */
export function activeUsageModel(
  provider: UsageProvider,
  runs: Pick<AgentRuns, "choiceFor" | "claudeRuns" | "claude">,
  catalogs: Pick<ModelCatalogs, "of" | "defaults">,
): UsageModel | undefined {
  if (provider === "claude") {
    const picked =
      runs.claude.model || catalogs.defaults.of("claude")?.model || "";
    const listed = runs.claudeRuns;
    const id = [picked, listed?.resolved ?? listed?.id]
      .filter((part, i, list) => part && list.indexOf(part) === i)
      .join(" ");
    if (!id && !listed?.name) return undefined;
    return { id, name: listed?.name };
  }
  const picked = runs.choiceFor(provider)?.model ?? "";
  const id = picked || catalogs.defaults.of(provider)?.model || "";
  if (!id) return undefined;
  const listed = catalogs.of(provider)?.find((m) => m.id === id);
  return { id, name: listed?.name };
}

/** The composer's usage rings for agents with plan limits, or the thread's token cost otherwise. */
export function ComposerUsageControl({
  to,
  runs,
  catalogs,
  accounts,
  threadCost,
}: {
  to: Recipient;
  runs: Pick<AgentRuns, "choiceFor" | "claudeRuns" | "claude">;
  catalogs: Pick<ModelCatalogs, "of" | "defaults">;
  accounts: ThreadAccounts;
  threadCost?: ThreadCost;
}) {
  if (!reportsUsage(to)) {
    return threadCost ? <ThreadCostButton cost={threadCost} /> : null;
  }
  const model = activeUsageModel(to, runs, catalogs);
  return (
    <UsageRing
      provider={to}
      modelId={model?.id}
      modelName={model?.name}
      account={accounts.of(to)}
      accountName={accountUsageName(to, accounts)}
    />
  );
}

/**
 * Rings in the composer with the limits that cap the picked model. Hovering
 * shows every reported window, with other pools dimmed.
 */
export const UsageRing = memo(function UsageRing({
  provider,
  modelId,
  modelName,
  account,
  accountName,
}: {
  provider: UsageProvider;
  modelId?: string;
  modelName?: string;
  /** Whose limits: the thread's account; the usual sign-in when left out. */
  account?: string;
  /** How a reset's confirm names that account; the agent's name when left out. */
  accountName?: string;
}) {
  const {
    readings: [reading],
    refresh: refreshUsage,
  } = useUsageReadings([{ provider, account }]);
  const usage = reading.data;
  const now = useNow(20_000);
  const pinOnClick = usePinOnClick();
  const agent = agentName(provider);
  const model =
    modelId || modelName ? { id: modelId ?? "", name: modelName } : undefined;
  const state = ringState(usage, now, model);
  // Nothing to show when the provider reports no limits at all.
  if (usage && !usage.windows.length) return null;
  const hot = state?.pace === "hot" || state?.pace === "spent";
  return (
    <Popover.Root onOpenChange={pinOnClick}>
      <Popover.Trigger
        openOnHover
        delay={150}
        closeDelay={150}
        type="button"
        className="composer-control usage-ring-trigger"
        data-pace={state?.pace ?? "ok"}
        data-loading={state ? undefined : true}
        aria-label={
          state ? `${agent} usage: ${state.label}` : `${agent} usage, checking…`
        }
      >
        <UsageDial meters={state?.activeMeters ?? []} />
        {hot && <Flame size={8} className="usage-ring-flame" aria-hidden />}
      </Popover.Trigger>
      <UsageRingPopup
        agent={agent}
        usage={usage}
        state={state}
        account={account}
        accountName={accountName}
        refreshing={reading.isFetching}
        onRefresh={() => void refreshUsage(provider, account).catch(() => {})}
      />
    </Popover.Root>
  );
});

function UsageRingPopup({
  agent,
  usage,
  state,
  account,
  accountName,
  refreshing,
  onRefresh,
}: {
  agent: string;
  usage: ProviderUsage | undefined;
  state: RingState | null;
  account?: string;
  accountName?: string;
  refreshing: boolean;
  onRefresh: () => void;
}) {
  const qc = useQueryClient();
  const resets = usage?.provider === "codex" ? usage.resets : null;
  return (
    <Popover.Portal>
      <Popover.Positioner
        className="composer-popup-positioner"
        side="top"
        align="end"
        sideOffset={6}
      >
        <Popover.Popup
          data-shortcut-passive
          className="composer-select-popup usage-ring-popup"
          data-grouped={usage?.windows.some((w) => !!w.label) || undefined}
        >
          <div className="usage-ring-header">
            <p className="usage-ring-heading">{agent} usage</p>
            {resets && (
              <ResetUsage
                key={account ?? SYSTEM_ACCOUNT}
                compact
                accountId={account ?? SYSTEM_ACCOUNT}
                accountName={accountName ?? agent}
                resets={resets}
                low={state?.meters.some((m) => m.leftPercent < 20) ?? false}
                onUsage={(next) =>
                  putUsage(qc, "codex", account ?? SYSTEM_ACCOUNT, next)
                }
              />
            )}
            <button
              type="button"
              className="icon-button usage-ring-refresh"
              onClick={onRefresh}
              disabled={refreshing}
              aria-label={`Refresh ${agent} usage`}
              title="Refresh"
            >
              <RefreshCw
                size={12}
                className={refreshing ? "spin" : undefined}
                aria-hidden
              />
            </button>
          </div>
          {state ? (
            state.meters.map((meter) => (
              <UsageRow key={meter.id ?? meter.kind} meter={meter} />
            ))
          ) : (
            <p className="usage-ring-status">Checking usage…</p>
          )}
          {usage?.credits && <CreditsRow credits={usage.credits} />}
        </Popover.Popup>
      </Popover.Positioner>
    </Popover.Portal>
  );
}

function UsageRow({ meter }: { meter: RingMeter }) {
  const hot = meter.pace === "hot" || meter.pace === "spent";
  const gap = paceGap(meter);
  return (
    <div
      className="usage-row"
      data-kind={meter.kind}
      data-ring={meter.ringRole}
      data-pace={meter.pace}
      data-inactive={meter.active ? undefined : ""}
    >
      <div className="usage-row-top">
        <span className="usage-row-label">
          <span className="usage-row-dot" aria-hidden />
          {meter.label}
        </span>
        <span className="usage-row-value">
          {gap && <span className="usage-row-gap">{gap}</span>}
          <span className="usage-row-left">{meter.leftPercent}% left</span>
        </span>
      </div>
      <div
        className="usage-row-track"
        role="progressbar"
        aria-label={`${meter.label} usage left`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={meter.leftPercent}
        aria-valuetext={[`${meter.leftPercent}% left`, gap]
          .filter(Boolean)
          .join(", ")}
      >
        <span
          className="usage-row-fill"
          style={{ width: `${meter.leftPercent}%` }}
        />
        {meter.paceLeftPercent != null && (
          <span
            className="usage-pace-mark"
            style={{ left: `${meter.paceLeftPercent}%` }}
          />
        )}
      </div>
      <div className="usage-row-bottom">
        <span>{meter.resetLabel ?? ""}</span>
        {meter.limitLabel && (
          <span className="usage-row-limit">
            {hot && <Flame size={10} aria-hidden />}
            {meter.limitLabel}
          </span>
        )}
      </div>
    </div>
  );
}
