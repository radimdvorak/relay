package dev.relay.android.features.thread

// The desktop's WatchNotes (src/features/watch): what the side check flagged in
// a turn, as a quiet card after the answer. Closing one, any of the three ways,
// keeps it out of the turn.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.features.markdown.Markdown
import dev.relay.android.features.markdown.OpenLink
import dev.relay.android.remote.Bridge
import dev.relay.android.remote.model.RemoteJson
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Eye
import io.github.lyxnx.compose.ui.tablericons.outline.X
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.CircleTone
import androidx.compose.foundation.layout.heightIn

/** shared/watch.ts's WatchNote, read here: the message model keeps notes untyped. */
@Serializable
data class WatchNote(
    val id: String,
    val tag: String,
    val line: String,
    val title: String,
    val points: List<String> = emptyList(),
    val diff: WatchDiff? = null,
    val steer: String? = null,
    val agent: WatchAgent? = null,
    val closed: Boolean? = null,
)

@Serializable data class WatchDiff(val file: String, val lines: List<String>)
@Serializable data class WatchAgent(val id: String, val label: String)

fun watchNotes(notes: List<JsonElement>?): List<WatchNote> = notes.orEmpty().mapNotNull {
    try {
        RemoteJson.decodeFromJsonElement(WatchNote.serializer(), it)
    } catch (_: Exception) {
        null
    }
}

@Composable
fun WatchNotes(
    chatId: String,
    messageId: String,
    notes: List<JsonElement>?,
    /** Who Tell puts the message in front of. */
    agent: String,
    /** Paths in the notes, opened as the answer's are. */
    openPath: OpenLink?,
    /** Puts a message for the agent in the composer. */
    onSteer: ((String) -> Unit)?,
) {
    val remote = LocalThreadRemote.current ?: return
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // Hidden at once; the thread's copy catches up when the close lands.
    val closed = remember { mutableStateListOf<String>() }
    val shown = remember(notes) { watchNotes(notes) }.filter { it.closed != true && it.id !in closed }
    if (shown.isEmpty()) return
    // Older desktops can't take a close from a phone; the notes still read.
    val closable = (remote.overview.value?.bridge ?: 1) >= Bridge.AWAY
    fun close(note: WatchNote, how: String) {
        closed += note.id
        val reading = open == note.id
        scope.launch {
            try {
                remote.desktop("closeWatchNote", chatId, messageId, note.id, how, reading)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                closed -= note.id
            }
        }
    }
    val c = RelayTheme.colors
    for (note in shown) {
        val reading = open == note.id
        QuietCard(Modifier.padding(top = 4.dp), padding = 0.dp, gap = 0.dp) {
            Row(
                Modifier.heightIn(min = 40.dp).padding(start = 16.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TraceGap),
            ) {
                Icon(TablerIcons.Outline.Eye, null, size = TraceIcon, tint = c.inkSoft)
                Text(note.tag, style = RelayTheme.type.meta.copy(fontWeight = FontWeight.Medium))
                Text(
                    note.agent?.let { "subagent · ${it.label}" } ?: "this turn",
                    Modifier.weight(1f), style = RelayTheme.type.small, color = c.inkFaint, maxLines = 1,
                )
                if (closable) CircleButton(
                    TablerIcons.Outline.X, "Dismiss", { close(note, "dismissed") },
                    size = CircleSize.Small, tone = CircleTone.Clear,
                )
            }
            Text(note.line, Modifier.padding(horizontal = 16.dp), style = RelayTheme.type.meta.copy(lineHeight = 20.sp))
            if (reading) Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(note.title, style = RelayTheme.type.meta.copy(fontWeight = FontWeight.SemiBold))
                if (note.points.isNotEmpty()) Markdown(note.points.joinToString("\n") { "- $it" }, small = true, openPath = openPath)
                note.diff?.let { diff ->
                    Column(Modifier.padding(top = 4.dp).fillMaxWidth().background(c.surface, TraceBox).padding(10.dp)) {
                        Text(diff.file, Modifier.padding(bottom = 4.dp), style = RelayTheme.type.small, color = c.inkSoft)
                        for (line in diff.lines) {
                            val tint = when (line.firstOrNull()) {
                                '+' -> c.added.copy(alpha = 0.14f)
                                '-' -> c.removed.copy(alpha = 0.14f)
                                else -> null
                            }
                            Text(
                                line, if (tint != null) Modifier.fillMaxWidth().background(tint) else Modifier,
                                style = traceCode(),
                            )
                        }
                    }
                }
            }
            Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                NoteAction(if (reading) "Less" else "Learn more") { open = if (reading) null else note.id }
                if (onSteer != null) NoteAction("Tell $agent") {
                    onSteer(note.steer ?: "About this: ${note.line}")
                    if (closable) close(note, "told")
                }
                if (closable) NoteAction("I know this") { close(note, "known") }
            }
        }
    }
}

@Composable
private fun NoteAction(label: String, onPress: () -> Unit) {
    Text(
        label,
        Modifier.pressable(onClick = onPress, scale = false).padding(horizontal = 8.dp, vertical = 10.dp),
        style = RelayTheme.type.meta.copy(fontWeight = FontWeight.Medium),
        color = RelayTheme.colors.inkSoft,
    )
}
