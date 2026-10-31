import { useState, type ReactNode } from "react";
import { Check } from "lucide-react";
import {
  tailscaleAndroid,
  type PhonePairing,
  type PhoneRemoteState,
  type PhoneTailnet,
} from "../../../shared/remote";
import { androidAppDownload } from "../../../shared/updates";
import { useCopy } from "../../lib/useCopy";
import { QrCode } from "./qr/QrCode";

type CodeShown = "tailscale" | "app";

/** Tailscale and the app on the phone, then the pairing code. */
export function PhoneSetupSteps({
  data,
  busy,
  pairing,
  now,
  onPair,
}: {
  data: PhoneRemoteState;
  busy: boolean;
  /** The code on show; undefined once it's used or expired. */
  pairing?: PhonePairing;
  now: number;
  onPair: () => void;
}) {
  const [code, setCode] = useState<CodeShown>();
  const toggle = (which: CodeShown) =>
    setCode((open) => (open === which ? undefined : which));
  return (
    <ol className="phone-setup" aria-label="Set up a phone">
      <TailscaleStep
        data={data}
        open={code === "tailscale"}
        onToggle={() => toggle("tailscale")}
      />
      <AppStep open={code === "app"} onToggle={() => toggle("app")} />
      <PairStep busy={busy} pairing={pairing} now={now} onPair={onPair} />
    </ol>
  );
}

function TailscaleStep({
  data,
  open,
  onToggle,
}: {
  data: PhoneRemoteState;
  open: boolean;
  onToggle: () => void;
}) {
  const phone = data.tailnet.phones?.find((p) => p.online);
  return (
    <Step
      n={1}
      done={!!phone}
      title={
        data.lan
          ? "Tailscale on your phone, optional"
          : "Tailscale on your phone"
      }
      hint={
        data.lan && !phone
          ? "Not needed on the same Wi-Fi. With it the phone reaches Relay from anywhere."
          : phoneHint(data.tailnet)
      }
      action={
        <button onClick={onToggle}>
          {open ? "Hide code" : "Get Tailscale"}
        </button>
      }
    >
      {open && (
        <CodePanel text={tailscaleAndroid} label="Tailscale QR code">
          <p>
            Scan with the phone's camera to get Tailscale from Google Play, then
            sign in with the account this computer uses.
          </p>
          <p className="setting-muted">
            The phone and this computer then share a private network.
            {data.lan
              ? " The phone uses the local network when it's on the same one, Tailscale when it isn't."
              : " Relay listens on nothing else, so the Wi-Fi around you can't reach it."}
          </p>
        </CodePanel>
      )}
    </Step>
  );
}

function AppStep({ open, onToggle }: { open: boolean; onToggle: () => void }) {
  return (
    <Step
      n={2}
      title="Relay on your phone"
      hint="The Android app from Relay's releases."
      action={
        <button onClick={onToggle}>{open ? "Hide code" : "Get the app"}</button>
      }
    >
      {open && (
        <CodePanel text={androidAppDownload} label="Download QR code">
          <p>
            Scan with the phone's camera to download the newest Relay app. Open
            it from the download, and allow installs from the browser when
            Android asks; it asks only the first time.
          </p>
          <p className="setting-muted">
            Later versions come from this computer, so the app stays in step
            with it.
          </p>
        </CodePanel>
      )}
    </Step>
  );
}

function PairStep({
  busy,
  pairing,
  now,
  onPair,
}: {
  busy: boolean;
  pairing?: PhonePairing;
  now: number;
  onPair: () => void;
}) {
  const [copied, copy] = useCopy();
  return (
    <Step
      n={3}
      title="Pair"
      hint="Scan the code from the Relay app on the phone."
      action={
        <button className="primary" disabled={busy} onClick={onPair}>
          {pairing ? "New code" : "Show pairing code"}
        </button>
      }
    >
      {pairing && (
        <CodePanel text={pairing.url} label="Pairing QR code">
          <p>
            Open Relay on the phone and scan this code. It works once and
            expires in{" "}
            {Math.max(1, Math.ceil((pairing.expiresAt - now) / 60000))} min.
          </p>
          <p className="setting-muted">
            The code also pins this computer's key, so the phone only ever talks
            to this Relay, encrypted.
          </p>
          <button onClick={() => copy(pairing.url)}>
            {copied ? "Copied" : "Copy pairing link"}
          </button>
        </CodePanel>
      )}
    </Step>
  );
}

function phoneHint(tailnet?: PhoneTailnet) {
  const phones = tailnet?.phones;
  const online = phones?.find((p) => p.online);
  if (online) return `${online.name} is on your tailnet.`;
  if (phones?.length)
    return `${phones[0]!.name} is on your tailnet but offline. Open Tailscale on it.`;
  return "Install Tailscale and sign in with the account this computer uses.";
}

function Step({
  n,
  done,
  title,
  hint,
  action,
  children,
}: {
  n: number;
  done?: boolean;
  title: string;
  hint: string;
  action: ReactNode;
  children?: ReactNode;
}) {
  return (
    <li className={done ? "phone-step done" : "phone-step"}>
      <span
        className="phone-step-mark"
        role="img"
        aria-label={done ? `Step ${n}, done` : `Step ${n}`}
      >
        {done ? <Check size={12} strokeWidth={3} /> : n}
      </span>
      <div className="settings-row-text">
        <span>{title}</span>
        <small>{hint}</small>
      </div>
      <div className="settings-row-control">{action}</div>
      {children && <div className="phone-step-below">{children}</div>}
    </li>
  );
}

function CodePanel({
  text,
  label,
  children,
}: {
  text: string;
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="phone-pairing">
      <QrCode text={text} label={label} />
      <div>{children}</div>
    </div>
  );
}
