package dev.relay.android.features.thread

// The desktop's Progress section (src/features/agent-checklist) for a phone: the step the agent
// is on over the composer, and a sheet with all of them.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.relay.android.features.thread.logic.checklistProgress
import dev.relay.android.features.thread.logic.currentStep
import dev.relay.android.remote.model.ChecklistStep
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronUp
import io.github.lyxnx.compose.ui.tablericons.outline.ListCheck
import io.github.lyxnx.compose.ui.tablericons.outline.X

/** The step the agent is on, over the composer; a tap lists them all. One line, whatever the step says. */
@Composable
fun ProgressStrip(steps: List<ChecklistStep>) {
    val c = RelayTheme.colors
    var listing by remember { mutableStateOf(false) }
    StripBox(onPress = { listing = true }) {
        Icon(TablerIcons.Outline.ListCheck, null, size = TraceIcon, tint = c.inkSoft)
        Text(currentStep(steps)?.text ?: "Progress", Modifier.weight(1f), style = RelayTheme.type.meta, maxLines = 1)
        Text(checklistProgress(steps), style = RelayTheme.type.meta, color = c.inkSoft, maxLines = 1)
        Icon(TablerIcons.Outline.ChevronUp, "Lists the steps", tint = c.inkFaint)
    }
    if (listing) Sheet(onDismiss = { listing = false }, title = "Progress · ${checklistProgress(steps)}") {
        ProgressSteps(steps)
    }
}

/** The sheet's list, roomier than a row's. */
@Composable
internal fun ProgressSteps(steps: List<ChecklistStep>) =
    ChecklistSteps(steps, Modifier.padding(bottom = 8.dp), style = RelayTheme.type.body, gap = 12)

/** Each step and where it stands. Still: the running call is what moves. */
@Composable
internal fun ChecklistSteps(steps: List<ChecklistStep>, modifier: Modifier = Modifier, style: TextStyle = traceText(), gap: Int = 6) {
    val c = RelayTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap.dp)) {
        for (step in steps) Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = spoken(step.state) },
            horizontalArrangement = Arrangement.spacedBy(TraceGap + 2.dp),
        ) {
            FirstLine(style) { StepMark(step.state) }
            Text(step.text, Modifier.weight(1f), style = style, color = if (step.state == "now") c.ink else c.inkSoft)
        }
    }
}

/** Centres the mark on its step's first line. */
@Composable
private fun FirstLine(style: TextStyle, mark: @Composable () -> Unit) {
    val line = with(LocalDensity.current) { style.lineHeight.toDp() }
    // At a small font size the line is shorter than the mark, and padding can't be negative.
    Box(Modifier.padding(top = ((line - TraceIcon) / 2).coerceAtLeast(0.dp))) { mark() }
}

/** Ticked, half-filled while the agent is on it, crossed when it failed, or an empty ring. */
@Composable
private fun StepMark(state: String) {
    val c = RelayTheme.colors
    when (state) {
        "done" -> Icon(TablerIcons.Outline.Check, null, size = TraceIcon, tint = c.inkSoft)
        "failed" -> Icon(TablerIcons.Outline.X, null, size = TraceIcon, tint = c.danger)
        else -> {
            val now = state == "now"
            val tint = if (now) c.accent else c.inkFaint
            Canvas(Modifier.size(TraceIcon)) {
                drawCircle(tint, radius = 6.dp.toPx(), style = Stroke(1.4.dp.toPx()))
                if (now) drawCircle(tint, radius = 2.8.dp.toPx())
            }
        }
    }
}

private fun spoken(state: String) = when (state) {
    "done" -> "Done"
    "now" -> "Working on it"
    "failed" -> "Failed"
    else -> "Not started"
}
