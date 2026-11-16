import { MoveToWorktreeDialog } from "../changes/MoveToWorktreeDialog";
import { ArchiveWorktreeDialog } from "./ArchiveWorktreeDialog";
import { DeleteThreadDialog } from "./DeleteThreadDialog";
import { SnoozeAwayDialog } from "./SnoozeAwayDialog";
import { SnoozePickDialog } from "./SnoozeMenu";
import type { ThreadActions } from "./useThreadActions";

/** What a thread's menu asks before it acts on the thread. */
export function ThreadDialogs({ actions }: { actions: ThreadActions }) {
  const { snoozing } = actions;
  return (
    <>
      {snoozing.picking && (
        <SnoozePickDialog
          onSnooze={(until) =>
            void actions.triage(snoozing.picking!, {
              kind: "snooze",
              until,
            })
          }
          onClose={() => snoozing.setPicking(undefined)}
        />
      )}
      {snoozing.away && (
        <SnoozeAwayDialog
          chat={snoozing.away.chat}
          onSnooze={snoozing.snoozeAway}
          onCancel={snoozing.cancelAway}
        />
      )}
      {actions.archiving && (
        <ArchiveWorktreeDialog
          chat={actions.archiving.chat}
          files={actions.archiving.files}
          onArchive={actions.archive}
          onCancel={actions.cancelArchive}
        />
      )}
      {actions.deletion.asking && (
        <DeleteThreadDialog
          chat={actions.deletion.asking}
          deleting={actions.deletion.deleting}
          onDelete={actions.deletion.confirm}
          onCancel={actions.deletion.cancel}
        />
      )}
      {actions.moving && (
        <MoveToWorktreeDialog
          chatId={actions.moving}
          onClose={() => actions.setMoving(undefined)}
        />
      )}
    </>
  );
}
