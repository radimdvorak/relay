package dev.relay.android.features.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import dev.relay.android.remote.Bridge
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.Sheet
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.AlarmSnooze
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.GitBranch
import io.github.lyxnx.compose.ui.tablericons.outline.Mail

/** A running thread settles once its answer ends, as the desktop's sidebar does; one asking doesn't. */
internal fun settleAction(chat: RemoteChatSummary): Pair<String, TriageAction>? = when {
    chat.waiting == true -> null
    chat.running == true -> "Settle when done" to TriageAction.SettleWhenDone
    else -> "Settle" to TriageAction.Settle
}

/** A held row's menu: settle, snooze until, mark unread, copy its branch. */
@Composable
internal fun ThreadMenu(
    chat: RemoteChatSummary,
    now: Long,
    bridge: Int,
    onTriage: (TriageAction) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    fun pick(run: () -> Unit) {
        onDismiss()
        run()
    }
    Sheet(onDismiss, title = chat.title) {
        Column {
            settleAction(chat)?.let { (label, action) ->
                ListRow(
                    label, subtitle = "Hide it until something new happens", leadingIcon = TablerIcons.Outline.Check,
                    onClick = { pick { onTriage(action) } },
                )
            }
            for (p in snoozePresets(now)) ListRow(
                "Snooze · ${p.label}", subtitle = wakeLabel(p.until, now), leadingIcon = TablerIcons.Outline.AlarmSnooze,
                onClick = { pick { onTriage(TriageAction.Snooze(p.until)) } },
            )
            if (bridge >= Bridge.MARKED_UNREAD)
                ListRow("Mark as unread", leadingIcon = TablerIcons.Outline.Mail, onClick = { pick { onTriage(TriageAction.Unread) } })
            chat.branch?.let { branch ->
                ListRow(
                    "Copy branch name", subtitle = branch, leadingIcon = TablerIcons.Outline.GitBranch,
                    onClick = { pick { onCopy(branch) } },
                )
            }
        }
    }
}
