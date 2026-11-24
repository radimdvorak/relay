package dev.relay.android.features.thread

// The rows around and under a message (mobile/src/ui/MessageView.tsx): status
// lines across the thread, handoffs and worktree setup.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.features.markdown.Markdown
import dev.relay.android.features.thread.logic.Clock
import dev.relay.android.features.thread.logic.agentError
import dev.relay.android.features.thread.logic.agentName
import dev.relay.android.features.thread.logic.compactedTokens
import dev.relay.android.features.thread.logic.copiedNote
import dev.relay.android.features.thread.logic.worktreeCommandNote
import dev.relay.android.remote.model.ChatMessage
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme

/** A quiet line of its own in the middle of the thread, for compactions and handoffs. */
@Composable
fun StatusRow(text: String, failed: Boolean = false, action: Pair<String, () -> Unit>? = null) {
    val c = RelayTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ThreadInset + 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = statusText(), color = if (failed) c.danger else c.inkFaint, textAlign = TextAlign.Center)
        action?.let { (label, onPress) ->
            Text(
                label, Modifier.pressable(onClick = onPress, scale = false).padding(horizontal = 12.dp, vertical = 6.dp),
                style = statusText().copy(fontWeight = FontWeight.Medium), color = c.inkSoft,
            )
        }
    }
}

@Composable
private fun statusText() = RelayTheme.type.small.copy(fontSize = 13.sp, lineHeight = 18.sp)

/** What a status line keeps folded: a note, a summary, a command's output. */
@Composable
private fun NoteBox(content: @Composable () -> Unit) {
    QuietCard(Modifier.padding(start = ThreadInset, end = ThreadInset, bottom = 12.dp)) { content() }
}

@Composable
fun HandoffRow(m: ChatMessage) {
    val handoff = m.handoff ?: return
    var open by rememberSaveable(m.id) { mutableStateOf(false) }
    val from = agentName(handoff.from)
    val switched = if (handoff.computer != null) "Continued on ${handoff.computer} · ${Clock().clock(m.created)}"
    else "Switched from $from to ${agentName(handoff.to)}${if (handoff.byRelay == true) " · $from couldn't write a note, Relay did" else ""}"
    val note = m.body.trim().takeIf { m.status == "complete" && it.isNotEmpty() }
    Column {
        StatusRow(
            when {
                m.status == "streaming" -> "$from is writing a handoff note for ${handoff.computer ?: agentName(handoff.to)}…"
                note != null -> switched
                else -> "$switched · no handoff note"
            },
            action = note?.let { (if (open) "Hide note" else "Show note") to { open = !open } },
        )
        if (open && note != null) NoteBox { Markdown(note, small = true) }
    }
}

/** The desktop's CompactionRow: where a session was compacted, and the summary the agent kept from it. */
@Composable
fun CompactionRow(m: ChatMessage) {
    var open by rememberSaveable(m.id) { mutableStateOf(false) }
    val summary = m.compactSummary?.takeIf { m.status == "complete" && it.isNotBlank() }
    Column {
        StatusRow(
            when {
                m.status == "streaming" -> "Compacting context…"
                m.status == "complete" ->
                    "Context compacted ${compactedTokens(m.compactedFrom, m.context?.usedTokens?.toDouble())}".trim()
                m.status == "cancelled" -> "Compaction stopped"
                m.error != null && m.signIn == null -> agentError(m.error).message
                else -> "Compaction failed"
            },
            failed = m.status == "failed",
            action = summary?.let { (if (open) "Hide summary" else "Show summary") to { open = !open } },
        )
        if (open && summary != null) NoteBox {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${agentName(m.provider)} kept this after compacting", style = statusText(), color = RelayTheme.colors.inkSoft)
                Markdown(summary, small = true)
            }
        }
        m.signIn?.let { Box(Modifier.padding(horizontal = ThreadInset)) { SignInNote(it) } }
    }
}

/** The desktop's WorktreeCommandRow: what the project's setup or teardown printed, and running setup again. */
@Composable
fun WorktreeCommandRow(m: ChatMessage, onRerun: (() -> Unit)?) {
    val run = m.worktreeCommand ?: return
    var open by rememberSaveable(m.id) { mutableStateOf(false) }
    val text = listOfNotNull(run.copied?.takeIf { it.isNotEmpty() }?.let(::copiedNote), run.output.takeIf { it.isNotEmpty() })
        .joinToString("\n\n")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StatusRow(
            worktreeCommandNote(run, m.status),
            failed = m.status == "failed" || m.status == "cancelled",
            action = text.takeIf { it.isNotEmpty() }?.let { (if (open) "Hide output" else "Show output") to { open = !open } },
        )
        if (onRerun != null) Text(
            "Run setup again",
            Modifier.pressable(onClick = onRerun, scale = false).padding(horizontal = 12.dp, vertical = 6.dp),
            style = statusText().copy(fontWeight = FontWeight.Medium),
            color = RelayTheme.colors.accent,
        )
        if (open && text.isNotEmpty()) NoteBox {
            SelectionContainer {
                Text("$ ${run.command}\n${text.takeLast(4000)}", style = traceCode(), color = RelayTheme.colors.inkSoft)
            }
        }
    }
}
