package dev.relay.android.features.thread

// What ThreadScreen puts around its messages: what stands in for them, the way to older ones,
// the queue at their end and the desktop's strips over the composer.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.relay.android.features.thread.logic.dockedChecklist
import dev.relay.android.features.thread.logic.threadChecklist
import dev.relay.android.remote.Bridge
import dev.relay.android.remote.client.LinkState
import dev.relay.android.remote.model.ChecklistStep
import dev.relay.android.remote.model.RemoteChat
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteQueued
import dev.relay.android.ui.Button
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme

@Composable
internal fun ColumnScope.NothingToRead(error: String?, link: LinkState, computer: String, onRetry: () -> Unit) {
    val c = RelayTheme.colors
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            link == LinkState.Online && error != null -> {
                Text(error, style = RelayTheme.type.meta, color = c.inkSoft, textAlign = TextAlign.Center)
                Button("Try again", onClick = onRetry)
            }
            // Still connecting isn't a failure yet: wait for it rather than saying the copy is missing.
            link == LinkState.Connecting -> Spinner(color = c.inkSoft)
            link != LinkState.Online -> Text(
                "This thread hasn’t been opened on this phone, so there’s no copy to read until $computer is back.",
                style = RelayTheme.type.meta,
                color = c.inkSoft,
                textAlign = TextAlign.Center,
            )
            else -> Spinner(color = c.inkSoft)
        }
    }
}

@Composable
internal fun Earlier(earlier: Int, canLoad: Boolean, onLoad: () -> Unit) {
    val c = RelayTheme.colors
    val page = Bridge.REMOTE_HISTORY
    val label = if (canLoad)
        "Load ${minOf(page, earlier)} earlier ${if (earlier == 1) "message" else "messages"}${if (earlier > page) " of $earlier" else ""}"
    else "$earlier earlier ${if (earlier == 1) "message is" else "messages are"} on your computer"
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            Modifier.then(if (canLoad) Modifier.pressable(onClick = onLoad, scale = false) else Modifier).padding(16.dp),
            style = RelayTheme.type.meta.copy(fontWeight = if (canLoad) FontWeight.Medium else FontWeight.Normal),
            color = if (canLoad) c.inkSoft else c.inkFaint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun Queue(t: RemoteChat, running: Boolean, actions: ThreadActions, onEdit: suspend (RemoteQueued) -> Unit) {
    QueueList(
        waitingInQueue(t.queue), t.scheduled, running,
        compacting = t.messages.any { it.compaction == true && it.status == "streaming" },
        paused = t.queuePaused == true,
        onSteer = { actions.queue("steer", it) },
        onEdit = onEdit,
        onRemove = { actions.queue("remove", it) },
        onMove = { messageId, index -> actions.queue("move", messageId, index) },
    )
}

@Composable
internal fun Strips(
    t: RemoteChat?,
    summary: RemoteChatSummary?,
    rootId: String?,
    running: Boolean,
    actions: ThreadActions,
) {
    if (rootId != null) return
    summary?.goal?.let { GoalStrip(it, running) }
    summary?.limitResume?.takeIf { !running }?.let { LimitStrip(it, actions::setLimitResume) }
    t?.stopped?.items?.takeIf { it.isNotEmpty() }?.let { StoppedStrip(it, actions::resolveStopped) }
    val messages = t?.messages.orEmpty()
    // The steps as they stood when the sheet opened; the sheet outlives the strip.
    var listedSteps by remember { mutableStateOf<List<ChecklistStep>?>(null) }
    remember(messages, running) { dockedChecklist(messages, running) }?.let { steps ->
        ProgressStrip(steps) { listedSteps = steps }
    }
    listedSteps?.let { held ->
        val steps = remember(messages) { threadChecklist(messages) } ?: held
        ProgressSheet(steps) { listedSteps = null }
    }
}
