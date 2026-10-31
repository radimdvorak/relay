import { useNow } from "../../lib/useNow";
import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Check } from "lucide-react";
import {
  tailscaleDownload,
  type PhonePairing,
  type PhoneRemoteState,
  type PhoneTailnet,
} from "../../../shared/remote";
import { api } from "../../lib/api";
import { ErrorBox } from "../../ui/ui";
import { relativeDate } from "../../lib/relative-date";
import {
  SettingsCard,
  SettingsFooter,
  SettingsRow,
  Switch,
} from "../../ui/SettingsCard";
import { FirewallRow } from "./FirewallRow";
import { PhoneSetupSteps } from "./PhoneSetupSteps";
import { usePoll } from "../../lib/poll";

/**
 * Phone access runs over Tailscale, and over the local network when that's
 * allowed, so Settings walks through it: this computer on Tailscale, the
 * switches, then the phone's Tailscale, the app and the pairing code. Once a
 * phone is paired the steps fold away.
 */
export function PhoneRemoteSettings() {
  const poll = usePoll();
  const qc = useQueryClient();
  const state = useQuery({
    queryKey: ["phone-remote"],
    queryFn: () => api.phoneRemoteState(),
    // Notices Tailscale coming up and phones coming and going while Settings is open.
    refetchInterval: poll(3000),
  });
  // `before`: phones paired when the code was made. A new one used the code up.
  const [pairing, setPairing] = useState<
    PhonePairing & { before: ReadonlySet<string> }
  >();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const [another, setAnother] = useState(false);
  const now = useNow(15000, !!pairing);
  const data = state.data;
  // Computers that paired to hand threads over have their own list, under Computers.
  const phones = data?.devices.filter((d) => d.kind !== "computer") ?? [];
  const paired = phones.length;
  // Gone for good, not hidden, so removing a phone can't bring a used code back.
  const used = pairing && phones.some((d) => !pairing.before.has(d.id));
  useEffect(() => {
    if (used) setPairing(undefined);
  }, [used]);
  const showing = pairing && !used && pairing.expiresAt > now;
  useEffect(() => setAnother(false), [paired]);
  const run = (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(undefined);
    return action()
      .then(() => qc.invalidateQueries({ queryKey: ["phone-remote"] }))
      .catch(setError)
      .finally(() => setBusy(false));
  };
  const changeAccess = (change: () => Promise<unknown>) =>
    run(async () => {
      setPairing(undefined);
      await change();
    });
  const pair = () =>
    void run(async () =>
      setPairing({
        ...(await api.phonePairing()),
        before: new Set(phones.map((d) => d.id)),
      }),
    );
  const steps = data?.listening && (!paired || another || showing);
  return (
    <>
      <SettingsCard className="phone-remote">
        <TailscaleRow tailnet={data?.tailnet} lan={!!data?.lan} />
        <PhoneAccessRows data={data} busy={busy} onChange={changeAccess} />
        {steps && (
          <PhoneSetupSteps
            data={data}
            busy={busy}
            pairing={showing ? pairing : undefined}
            now={now}
            onPair={pair}
          />
        )}
        {data?.listening && (
          <PairedPhones
            phones={phones}
            busy={busy}
            onRemove={(id) => void run(() => api.revokePhone(id))}
          />
        )}
        {data?.listening && (
          <SettingsFooter
            note={
              paired
                ? "Removing a phone disconnects it right away."
                : "Phones can read and send messages, answer the agent's questions and view diffs. They can't open terminals or change settings."
            }
          >
            {another ? (
              <button onClick={() => setAnother(false)}>Hide steps</button>
            ) : (
              !steps && (
                <button onClick={() => setAnother(true)}>
                  Set up another phone
                </button>
              )
            )}
          </SettingsFooter>
        )}
      </SettingsCard>
      {!!(error || state.error) && <ErrorBox error={error || state.error} />}
    </>
  );
}

/** Whether phones may connect, over which networks, and what keeps them out. */
function PhoneAccessRows({
  data,
  busy,
  onChange,
}: {
  data?: PhoneRemoteState;
  busy: boolean;
  onChange: (change: () => Promise<unknown>) => Promise<unknown>;
}) {
  // The switch flips at once; the state catches up when the server has.
  const [switching, setSwitching] = useState<boolean>();
  return (
    <>
      <SettingsRow label="Allow phone connections" hint={switchHint(data)}>
        <Switch
          label="Allow phone connections"
          checked={switching ?? !!data?.enabled}
          disabled={!data}
          onChange={(enabled) => {
            setSwitching(enabled);
            void onChange(() => api.setPhoneRemote(enabled)).finally(() =>
              setSwitching(undefined),
            );
          }}
        />
      </SettingsRow>
      <SettingsRow label="Local network" hint={lanHint(data)}>
        <Switch
          label="Local network"
          checked={!!data?.lan}
          disabled={!data || busy}
          onChange={(lan) => void onChange(() => api.setPhoneRemoteLan(lan))}
        />
      </SettingsRow>
      {data?.listening && <FirewallRow hosts={data.hosts} />}
    </>
  );
}

function PairedPhones({
  phones,
  busy,
  onRemove,
}: {
  phones: PhoneRemoteState["devices"];
  busy: boolean;
  onRemove: (id: string) => void;
}) {
  return phones.map((d) => (
    <SettingsRow
      key={d.id}
      label={d.name}
      hint={
        <>
          {d.online
            ? "Connected now"
            : d.lastSeen
              ? `Last seen ${relativeDate(new Date(d.lastSeen).toISOString())}`
              : "Paired"}
          <br />
          {d.app
            ? `Relay ${d.app.version}`
            : "App version unknown until the phone runs a newer Relay"}
        </>
      }
    >
      <button
        aria-label={`Remove ${d.name}`}
        disabled={busy}
        onClick={() => onRemove(d.id)}
      >
        Remove
      </button>
    </SettingsRow>
  ));
}

function TailscaleRow({
  tailnet,
  lan,
}: {
  tailnet?: PhoneTailnet;
  lan: boolean;
}) {
  const connected = tailnet?.status === "connected";
  return (
    <SettingsRow
      label={
        <span className="phone-tailscale">
          {connected && <Check size={14} aria-hidden />}
          Tailscale on this computer
        </span>
      }
      hint={
        !tailnet
          ? "Checking…"
          : connected
            ? `On your tailnet${tailnet.name ? ` as ${tailnet.name}` : ""}, at ${tailnet.addresses[0]}.`
            : tailnet.status === "stopped"
              ? "Installed, but not connected. Open Tailscale and sign in; Relay notices on its own."
              : lan
                ? "Optional with the local network on: it lets phones reach Relay away from this network too. Install it here and sign in; Relay notices on its own."
                : "Phones reach Relay over Tailscale, a private network between your own devices, or on the local network once that's on. Install it here and sign in; Relay notices on its own."
      }
    >
      {tailnet?.status === "missing" && (
        <button
          className="primary"
          onClick={() => void api.openExternal(tailscaleDownload)}
        >
          Get Tailscale
        </button>
      )}
    </SettingsRow>
  );
}

function switchHint(data?: PhoneRemoteState) {
  if (!data) return "Checking…";
  if (data.error) return data.error;
  if (data.listening && data.hosts.length)
    return `${reach(data)} reach Relay, at ${data.hosts.join(", ")}, port ${data.port}.`;
  if (data.listening)
    return "Listening, but this computer isn't on a network right now.";
  if (data.enabled)
    return "Waiting for Tailscale on this computer. Turn on Local network to let phones connect on the same Wi-Fi.";
  return "Off. Nothing listens for phones.";
}

function reach(data: PhoneRemoteState) {
  const tailnet = data.tailnet?.status === "connected";
  if (!data.lan) return "Only devices on your tailnet";
  return tailnet
    ? "Phones on this network or your tailnet"
    : "Phones on this network";
}

function lanHint(data?: PhoneRemoteState) {
  if (!data?.lan)
    return "Let phones on the same Wi-Fi connect directly, without Tailscale.";
  return "Phones on the same Wi-Fi connect directly, and move over by themselves when one answers. Still encrypted to this computer's key: others on the network can reach the port but get nothing.";
}
