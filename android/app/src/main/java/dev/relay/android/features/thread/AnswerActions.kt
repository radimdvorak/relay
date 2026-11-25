package dev.relay.android.features.thread

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.relay.android.features.readaloud.ReadAloudButton
import dev.relay.android.features.thread.logic.Clock
import dev.relay.android.features.thread.logic.answerSpeed
import dev.relay.android.features.thread.logic.formatCost
import dev.relay.android.features.thread.logic.formatTokens
import dev.relay.android.features.thread.logic.streamedOutput
import dev.relay.android.remote.model.ChatMessage
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowsSplit
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.Copy
import io.github.lyxnx.compose.ui.tablericons.outline.CornerUpLeft
import io.github.lyxnx.compose.ui.tablericons.outline.Dots
import kotlinx.coroutines.delay

private val ActionBox = 36.dp
private val ActionIcon = 18.dp

/** How fast an answer was written and what it cost: "1.2k out · 84 tok/s · $0.12"; null when nothing is known. */
internal fun answerFacts(message: ChatMessage): String? {
    val speed = answerSpeed(message.streamed)
    val output = streamedOutput(message.streamed)
    val written = if (output != null && speed != null) "${formatTokens(output)} out · $speed tok/s" else null
    val cost = message.cost?.takeIf { it > 0 }?.let(::formatCost)
    return listOfNotNull(written, cost).joinToString(" · ").ifEmpty { null }
}

/** One quiet icon under an answer. */
@Composable
fun AnswerAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = RelayTheme.colors.inkSoft) {
    Box(modifier.size(ActionBox).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, size = ActionIcon)
    }
}

/**
 * Under a finished answer: what to do with it, then when it was written and how fast, short of the
 * row's end, where the plan's rings float. The rest is in its menu (the dots).
 */
@Composable
fun AnswerActions(message: ChatMessage, onReply: (() -> Unit)?, onFork: (() -> Unit)?, onMore: (() -> Unit)? = null) {
    val c = RelayTheme.colors
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val now = rememberNow(60_000)
    val speed = answerSpeed(message.streamed)?.takeIf { streamedOutput(message.streamed) != null }?.let { "$it tok/s" }
    // The first icon, not its touch area, lines up with the text above.
    Row(Modifier.fillMaxWidth().offset(x = -(ActionBox - ActionIcon) / 2), verticalAlignment = Alignment.CenterVertically) {
        AnswerAction(
            if (copied) TablerIcons.Outline.Check else TablerIcons.Outline.Copy,
            if (copied) "Copied" else "Copy answer",
            {
                copyText(context, message.body)
                copied = true
            },
        )
        val remote = LocalThreadRemote.current
        if (remote != null && message.status == "complete" && message.body.isNotBlank())
            ReadAloudButton(remote, message.id, message.body)
        if (onFork != null) AnswerAction(TablerIcons.Outline.ArrowsSplit, "Fork into a new thread", onFork)
        if (onReply != null) AnswerAction(TablerIcons.Outline.CornerUpLeft, "Reply to message", onReply)
        if (onMore != null) AnswerAction(TablerIcons.Outline.Dots, "More for this answer", onMore)
        Text(
            listOfNotNull(Clock().sentLabel(message.ended ?: message.created, now), speed).joinToString(" · "),
            Modifier.weight(1f).padding(start = 8.dp),
            style = RelayTheme.type.small,
            color = c.inkFaint,
            maxLines = 1,
        )
    }
}
