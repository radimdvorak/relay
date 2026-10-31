import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../../lib/api";
import { windows } from "../../lib/mod-key";
import { SettingsRow } from "../../ui/SettingsCard";

const firewallKey = ["phone-firewall"];

/**
 * Reads the firewall again when a phone is about to pair. Reading takes
 * PowerShell a couple of seconds, so it isn't polled.
 */
export function useRecheckFirewall() {
  const qc = useQueryClient();
  return () =>
    void qc.invalidateQueries(
      { queryKey: firewallKey },
      { cancelRefetch: false },
    );
}

type Attempt = "declined" | "allowed";

/**
 * Windows Firewall keeping phones out, with the way to let Relay in. Mounted
 * while phones are allowed, so turning them on reads it afresh.
 */
export function FirewallRow({ hosts }: { hosts: string[] }) {
  const recheck = useRecheckFirewall();
  const [allowing, setAllowing] = useState(false);
  const [attempt, setAttempt] = useState<Attempt>();
  const [error, setError] = useState<string>();
  const firewall = useQuery({
    queryKey: [...firewallKey, hosts],
    queryFn: () => api.phoneFirewall(),
    enabled: windows,
    staleTime: Infinity,
    refetchOnMount: "always",
  });
  const blocked = firewall.data?.blocked ?? [];
  if (!blocked.length) return null;
  const allow = () => {
    setAllowing(true);
    setError(undefined);
    void api
      .allowPhonesThroughFirewall()
      .then((allowed) => {
        setAttempt(allowed ? "allowed" : "declined");
        if (allowed) recheck();
      })
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : String(e)),
      )
      .finally(() => setAllowing(false));
  };
  return (
    <SettingsRow
      label="Windows Firewall blocks phones"
      hint={
        error ??
        (firewall.isFetching
          ? "Checking the firewall again…"
          : firewallHint(blocked, attempt))
      }
    >
      <button className="primary" disabled={allowing} onClick={allow}>
        {allowing ? "Allowing…" : "Allow Relay"}
      </button>
    </SettingsRow>
  );
}

function firewallHint(blocked: string[], attempt?: Attempt) {
  if (attempt === "declined")
    return "Windows needs an administrator's yes to change the firewall. Allow Relay again to see its prompt.";
  if (attempt === "allowed")
    return "Still blocked after allowing: a rule Relay can't remove, likely set by your organization, keeps phones out.";
  return `Phones can't reach Relay at ${blocked.join(", ")}. Allowing it asks Windows for administrator rights once.`;
}
