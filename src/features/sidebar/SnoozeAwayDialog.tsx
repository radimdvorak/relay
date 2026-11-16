import type { ChatSummary } from "../../../shared/projects";
import { Modal } from "../../ui/ui";

/**
 * Snoozing a thread that runs on another computer: keep it quiet here alone,
 * or snooze it on that computer too, where its phones see it.
 */
export function SnoozeAwayDialog({
  chat,
  onSnooze,
  onCancel,
}: {
  chat: ChatSummary;
  onSnooze: (here: boolean) => void;
  onCancel: () => void;
}) {
  const computer = chat.sentTo?.computer ?? "the other computer";
  return (
    <Modal title="Snooze where?" onClose={onCancel}>
      <p>
        “{chat.title}” runs on {computer}. Snoozed only here, it stops notifying
        you on this computer and stays as it is on {computer}.
      </p>
      <div className="modal-actions">
        <button type="button" onClick={onCancel}>
          Cancel
        </button>
        <button type="button" onClick={() => onSnooze(false)}>
          Also on {computer}
        </button>
        <button
          type="button"
          className="primary"
          onClick={() => onSnooze(true)}
        >
          Only here
        </button>
      </div>
    </Modal>
  );
}
