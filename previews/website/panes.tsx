// The thread's terminal under the window's thread: the app's own drawer
// header, and a shell drawn by hand that starts a dev server.
import { useEffect, useState, type CSSProperties } from "react";
import { RotateCw, SquareTerminal, X } from "lucide-react";
import { PaneTabs } from "../../src/ui/PaneTabs";
import { IconButton } from "../../src/ui/ui";
import "../../src/features/terminal/terminal-drawer.css";
import { reducedMotion } from "./motion";

const command = "npm run dev";
const output = ["> relay dev", "> vite --host 127.0.0.1", "", "  VITE  ready in 412 ms", "", "  ➜  Local:   http://127.0.0.1:5177/"];
/** The output line after which the server counts as up. */
const READY = 4;

/** The thread's shell, starting a dev server; `onServing` fires once it is listening. */
export function TerminalDrawer({ onServing }: { onServing: () => void }) {
  // 0 types the command, then one output line per step.
  const [step, setStep] = useState(0);
  useEffect(() => {
    if (reducedMotion()) return setStep(output.length);
    if (step >= output.length) return;
    const timer = window.setTimeout(() => setStep(step + 1), step === 0 ? 1500 : 240);
    return () => window.clearTimeout(timer);
  }, [step]);
  const serving = step >= READY;
  useEffect(() => {
    if (serving) onServing();
  }, [serving]); // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <section className="terminal-drawer rw-terminal" aria-label="Terminal">
      <header className="terminal-drawer-header">
        <PaneTabs
          tabs={[{ key: "1", label: "Terminal", icon: <SquareTerminal size={13} /> }]}
          front="1"
          onFront={() => {}}
          onClose={() => {}}
          add={{ label: "New terminal", onClick: () => {} }}
        />
        <span className="terminal-drawer-cwd">relay</span>
        <IconButton label="Restart shell" onClick={() => {}}>
          <RotateCw size={13} />
        </IconButton>
        <IconButton label="Hide terminal" onClick={() => {}}>
          <X size={14} />
        </IconButton>
      </header>
      <pre>
        <b>~/relay main* ❯ </b>
        <span className="rw-typed" style={{ "--chars": command.length } as CSSProperties}>
          {command}
        </span>
        {"\n\n"}
        {output.slice(0, step).map((line, index) => (
          <span key={index} data-ok={line.includes("ready") || undefined}>
            {line}
            {"\n"}
          </span>
        ))}
      </pre>
    </section>
  );
}
