// The app's composer on sample data: its prompt box, attach button, toolbar
// and send or stop button, with the model a sample thread uses.
import { useRef, type ReactNode } from "react";
import { Paperclip } from "lucide-react";
import { ComposerToolbar } from "../../src/features/composer/ComposerToolbar";
import {
  useSampleControls,
  type SampleModel,
} from "../../src/features/composer/ComposerToolbarSample";
import { defaultToolbar } from "../../src/features/composer/composer-toolbar";
import { useToolbarFit } from "../../src/features/composer/useToolbarFit";
import {
  SendButton,
  StopButton,
} from "../../src/features/composer/ComposerSendButtons";

export function SampleComposer({
  model,
  running,
  approval,
  prompt,
  placeholder = "Ask about the code, plan a change, or build something…",
  className,
}: {
  model: SampleModel;
  running?: boolean;
  approval?: ReactNode;
  /** What's typed in it already. */
  prompt?: string;
  /** The app's own wording for the thread's state; ThreadComposer picks it. */
  placeholder?: string;
  className?: string;
}) {
  const controls = useSampleControls(model);
  const tools = useRef<HTMLDivElement>(null);
  // Sheds labels before the row wraps, as the app's composer does.
  useToolbarFit(tools);
  return (
    <div
      className={["thread-compose-wrap", className].filter(Boolean).join(" ")}
    >
      {approval}
      <form
        className="project-composer"
        onSubmit={(event) => event.preventDefault()}
      >
        <div className="composer-body">
          <div className="composer-prompt">
            {/* The editor's own markup (ProseMirror), so its CSS draws the placeholder. */}
            <div
              className="composer-prompt-input"
              role="textbox"
              aria-label="Message"
              aria-readonly
              data-placeholder={placeholder}
            >
              <p>{prompt ?? <br className="ProseMirror-trailingBreak" />}</p>
            </div>
          </div>
          <button
            type="button"
            className="composer-attach"
            aria-label="Attach files"
          >
            <Paperclip size={15} />
          </button>
        </div>
        <div className="composer-tools" ref={tools}>
          <ComposerToolbar
            layout={defaultToolbar}
            controls={controls}
            end={
              running ? (
                <StopButton armed={false} keys="" onStop={() => {}} />
              ) : (
                <SendButton
                  disabled={!prompt}
                  running={false}
                  sendKey="enter"
                  runningAction="queue"
                  onSendLater={() => {}}
                />
              )
            }
          />
        </div>
      </form>
    </div>
  );
}
