package dev.relay.android.features.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.features.thread.logic.requestCardMaxHeightDp
import dev.relay.android.remote.model.AgentRequest
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Haptic
import dev.relay.android.ui.Icon
import dev.relay.android.ui.IconButton
import dev.relay.android.ui.Text
import dev.relay.android.ui.rememberHaptics
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Dots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import dev.relay.android.ui.ButtonSize
import io.github.lyxnx.compose.ui.tablericons.filled.MessageCircle
import io.github.lyxnx.compose.ui.tablericons.filled.ShieldLock
import androidx.compose.ui.graphics.lerp

// The desktop's wording, from src/features/thread/AgentRequestCard.tsx.
private val decisionLabels = mapOf(
    "accept" to "Approve",
    "acceptForSession" to "Always",
    "decline" to "Decline",
    "cancel" to "Cancel",
)

private fun decisionsOf(request: AgentRequest): List<String> =
    request.decisions.orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

/** `{kind: "approval", decision}` or `{kind: "question", answers}`, as respondProjectChat takes it. */
typealias AgentResponse = Map<String, Any>

/** What the agent is waiting on: an approval, or questions to answer. */
@Composable
fun RequestCard(
    request: AgentRequest,
    /** Further requests queued behind this one. */
    more: Int,
    onRespond: suspend (AgentResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var busy by remember(request.id) { mutableStateOf(false) }
    var error by remember(request.id) { mutableStateOf<String?>(null) }
    var moreOptions by remember(request.id) { mutableStateOf(false) }
    val decisions = decisionsOf(request)
    val canRemember = "acceptForSession" in decisions
    // Room for the thread above it and the composer below; with the keyboard up its body scrolls.
    val keyboard = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
    val maxHeight = requestCardMaxHeightDp(LocalConfiguration.current.screenHeightDp.toFloat(), keyboard.value).dp
    fun respond(response: AgentResponse) {
        busy = true
        error = null
        scope.launch {
            try {
                onRespond(response)
                // A tap, not Success: that one is the turn's end, often a moment later.
                haptics.perform(Haptic.Light)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                busy = false
            }
        }
    }
    val c = RelayTheme.colors
    val head: @Composable ColumnScope.() -> Unit = {
        RequestHeading(request.kind, request.title)
        if (more > 0) Hint("$more more waiting after this")
        if (!request.note.isNullOrEmpty()) Hint(request.note)
        if (!request.detail.isNullOrEmpty()) SelectionContainer(
            Modifier.fillMaxWidth().heightIn(max = 160.dp).background(c.surface, RelayTheme.shapes.field)
                .verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            Text(request.detail, style = traceCode())
        }
    }
    Column(
        modifier.padding(horizontal = DockInset).padding(bottom = 8.dp).fillMaxWidth().heightIn(max = maxHeight)
            .dockCard(tint = c.waiting).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val questions = request.questions
        if (request.kind == "question" && !questions.isNullOrEmpty()) {
            Questions(questions, busy, head = head, onAnswer = { respond(mapOf("kind" to "question", "answers" to it)) })
        } else {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = head)
            // Full words on full-width buttons: the one place a wrong tap runs something.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                for (decision in listOf("decline", "acceptForSession", "accept").filter { it in decisions }) Button(
                    if (decision == "accept" && canRemember) "Once" else decisionLabels.getValue(decision),
                    onClick = { respond(mapOf("kind" to "approval", "decision" to decision)) },
                    modifier = Modifier.weight(1f),
                    variant = if (decision == "accept") ButtonVariant.Default else ButtonVariant.Secondary,
                    size = ButtonSize.Lg,
                    enabled = !busy,
                )
                if ("cancel" in decisions) IconButton(TablerIcons.Outline.Dots, "More approval options", { moreOptions = true }, enabled = !busy)
            }
        }
        error?.let { Hint(it, color = c.danger) }
    }
    if (moreOptions) MenuSheet(
        listOf(MenuItem(decisionLabels.getValue("cancel")) { respond(mapOf("kind" to "approval", "decision" to "cancel")) }),
        onClose = { moreOptions = false },
    )
}

/** Who is being waited on, in the colour the app keeps for that, then what it's about. */
@Composable
private fun RequestHeading(kind: String, title: String) {
    val c = RelayTheme.colors
    val question = kind != "approval"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TraceGap)) {
        Icon(if (question) TablerIcons.Filled.MessageCircle else TablerIcons.Filled.ShieldLock, null, size = 18.dp, tint = c.waiting)
        Text(
            if (question) "Waiting for your answer" else "Needs your approval",
            style = RelayTheme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            // Amber alone is too pale for words on a light card.
            color = lerp(c.waiting, c.ink, 0.45f),
        )
    }
    Text(title, style = RelayTheme.type.row.copy(fontWeight = FontWeight.SemiBold))
}

@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = RelayTheme.colors.inkSoft) {
    Text(text, modifier, style = RelayTheme.type.meta, color = color)
}
