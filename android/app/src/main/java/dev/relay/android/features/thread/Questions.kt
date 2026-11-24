package dev.relay.android.features.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.relay.android.remote.model.AgentQuestion
import dev.relay.android.ui.Button
import dev.relay.android.ui.Haptic
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Text
import dev.relay.android.ui.TextField
import dev.relay.android.ui.pressable
import dev.relay.android.ui.rememberHaptics
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/**
 * An agent's questions, one at a time. [deferred]: the agent keeps working, so
 * nothing goes out until Send; a tap only picks. [capped]: the parent has a
 * height cap and the question scrolls within it; in the thread's list there is
 * none, and a weighted child of an unbounded column measures 0 high.
 */
@Composable
fun ColumnScope.Questions(
    questions: List<AgentQuestion>,
    busy: Boolean,
    deferred: Boolean = false,
    capped: Boolean = true,
    head: (@Composable ColumnScope.() -> Unit)? = null,
    onAnswer: (Map<String, List<String>>) -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var index by remember(questions) { mutableIntStateOf(0) }
    val answers = remember(questions) { mutableStateMapOf<String, List<String>>() }
    var timer by remember { mutableStateOf<Job?>(null) }
    val question = questions[index.coerceAtMost(questions.lastIndex)]
    val options = question.options.orEmpty()
    // Moved on to one only words answer: the field is ready to type in.
    val writeOnly = options.isEmpty()
    val focus = remember { FocusRequester() }
    LaunchedEffect(index, writeOnly) {
        if (index > 0 && writeOnly) runCatching { focus.requestFocus() }
    }
    val chosen = answers[question.id].orEmpty()
    val labels = options.map { it.value ?: it.label }.toSet()
    val typed = chosen.filter { it !in labels }.joinToString(", ")
    fun advance() {
        if (index < questions.lastIndex) index++ else onAnswer(answers.toMap())
    }
    fun select(label: String) {
        if (busy) return
        haptics.perform(Haptic.Selection)
        answers[question.id] = when {
            question.multiple == true -> if (label in chosen) chosen - label else chosen + label
            else -> listOf(label)
        }
        timer?.cancel()
        // A single choice answers it; the short pause shows which one was picked.
        if (question.multiple != true && !deferred) timer = scope.launch {
            delay(200)
            advance()
        }
    }
    val c = RelayTheme.colors
    Column(
        if (capped) Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()) else Modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        head?.invoke(this)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                question.header?.takeIf { it.isNotEmpty() } ?: "Question",
                style = RelayTheme.type.meta.copy(fontWeight = FontWeight.Medium), color = c.inkSoft,
            )
            if (questions.size > 1) Hint("${index + 1} of ${questions.size}")
        }
        Text(question.question, style = RelayTheme.type.body)
        for (option in options) {
            val on = (option.value ?: option.label) in chosen
            Row(
                Modifier.fillMaxWidth().background(if (on) c.selected else c.surface, RelayTheme.shapes.field)
                    .pressable(onClick = { select(option.value ?: option.label) }, enabled = !busy, scale = false)
                    .semantics { selected = on }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Tick(on, several = question.multiple == true, Modifier.padding(top = 2.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(option.label, style = RelayTheme.type.body.copy(fontWeight = FontWeight.Medium))
                    option.description?.takeIf { it.isNotEmpty() }?.let { Hint(it) }
                }
            }
        }
    }
    val placeholder = if (options.isNotEmpty()) "Or write your own answer…" else "Your answer…"
    val write: (String) -> Unit = { text ->
        timer?.cancel()
        answers[question.id] = listOf(text)
    }
    if (question.allowCustomAnswer != false) {
        if (question.isSecret == true) SecretField(typed, write, placeholder, !busy, focus)
        else TextField(typed, write, placeholder = placeholder, enabled = !busy, singleLine = false, maxLines = 5, focusRequester = focus, fill = RelayTheme.colors.surface)
    }
    val answered = chosen.any { it.isNotBlank() }
    if (deferred || question.multiple == true || options.isEmpty() || typed.isNotEmpty()) Button(
        if (index < questions.lastIndex) "Next" else "Send answer",
        onClick = {
            timer?.cancel()
            advance()
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy && answered,
    )
}

/** Round for one of several, square where any number can be picked. */
@Composable
private fun Tick(on: Boolean, several: Boolean, modifier: Modifier = Modifier) {
    val c = RelayTheme.colors
    val shape = if (several) RoundedCornerShape(6.dp) else CircleShape
    Box(
        modifier.size(20.dp)
            .then(if (on) Modifier.background(c.strong, shape) else Modifier.border(1.5.dp, c.inkFaint, shape)),
        contentAlignment = Alignment.Center,
    ) { if (on) Icon(TablerIcons.Outline.Check, null, size = 14.dp, tint = c.onStrong) }
}

/** The ui TextField has no hidden entry; a secret answer stays dotted, as on the desktop. */
@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, placeholder: String, enabled: Boolean, focus: FocusRequester) {
    val c = RelayTheme.colors
    BasicTextField(
        value, onChange,
        Modifier.fillMaxWidth().focusRequester(focus).background(c.surface, RelayTheme.shapes.field).padding(horizontal = 14.dp, vertical = 12.dp),
        enabled = enabled,
        singleLine = true,
        textStyle = RelayTheme.type.row.copy(color = c.ink),
        cursorBrush = SolidColor(c.ink),
        visualTransformation = PasswordVisualTransformation(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = RelayTheme.type.row, color = c.inkFaint)
                inner()
            }
        },
    )
}
