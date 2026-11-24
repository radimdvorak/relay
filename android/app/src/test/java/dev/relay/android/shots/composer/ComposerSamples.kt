package dev.relay.android.shots.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.relay.android.features.composer.ComposerBox
import dev.relay.android.features.composer.ComposerField
import dev.relay.android.features.composer.ModelChoices
import dev.relay.android.features.composer.SendButtons
import dev.relay.android.features.composer.SendState
import dev.relay.android.features.composer.logic.AgentDefaults
import dev.relay.android.features.composer.logic.AgentModel
import dev.relay.android.features.composer.logic.modelRows
import dev.relay.android.features.dictation.MicCircle
import dev.relay.android.features.dictation.MicLook
import dev.relay.android.shots.SampleHome
import dev.relay.android.ui.theme.RelayTheme
import dev.relay.android.ui.theme.colorsFromDesktop

/** The user's desktop palette, as Computer mode wears it: near-equal sidebar and surface, a lavender send. */
val usersDesktop = colorsFromDesktop(
    mapOf(
        "kind" to "dark", "sidebar" to "#232326", "surface" to "#1e1e21", "toolbar" to "#242427", "inbox" to "#28282c",
        "text" to "#e1e1e5", "muted" to "#94949f", "border" to "#37373d", "hover" to "#34343c", "selected" to "#41414f",
        "accent" to "#aaa8e5", "accentSoft" to "#32313f", "onAccent" to "#1b1b22", "send" to "#aaa8e5", "onSend" to "#1b1b22",
    ),
)

private val five = listOf("low", "medium", "high", "xhigh", "max")

val claudeModels = listOf(
    AgentModel("fable", "Fable 5.2", "Most capable for ambitious work", efforts = five, defaultEffort = "high"),
    AgentModel("opus", "Opus 5.5", "For complex work and everyday tasks", efforts = five, defaultEffort = "medium"),
    AgentModel("sonnet", "Sonnet 5.5", "Most efficient for simpler tasks", efforts = five, defaultEffort = "medium"),
    AgentModel("haiku", "Haiku 5.5", "Fastest for quick answers"),
    AgentModel("opus-4", "Opus 4.1", legacy = true),
)

val sixtyModels = (1..60).map {
    AgentModel(
        "vendor-$it/model", if (it % 7 == 0) "Vendor $it Extremely Long Model Name (Preview 2026-11)" else "Model $it",
        "Through a provider with a description that runs on for a while", efforts = if (it % 2 == 0) listOf("low", "high") else emptyList(),
        defaultEffort = "low", group = "Vendor ${it % 5}",
    )
}

val agents = listOf("claude", "codex", "opencode", "cursor", "muse", "amp", "antigravity")

fun claudeRows(chosen: String = "opus") = modelRows("claude", claudeModels, chosen, AgentDefaults(model = "opus"))

/** A composer in one of its states, with a mic that hears [voice]. */
@Composable
internal fun SampleBox(
    state: SendState,
    expanded: Boolean,
    text: String = "",
    placeholder: String = "Message Claude",
    choices: ModelChoices = ModelChoices("claude", "Opus 5.5", "High"),
    voice: List<Float> = emptyList(),
    mic: Boolean = true,
    modifier: Modifier = Modifier,
    attachments: (@Composable () -> Unit)? = null,
) {
    ComposerBox(
        expanded, choices, {}, {}, {}, {},
        field = {
            ComposerField(remember(text) { TextFieldState(text) }, placeholder, remember { FocusRequester() }, {}, {}, {})
        },
        buttons = {
            SendButtons(
                state, {}, {}, {}, {},
                mic = if (!mic) null else ({ strong ->
                    MicCircle(
                        MicLook(strong || state.dictating, live = state.dictating, enabled = state.enabled),
                        levels = { voice }, onPress = { true }, onFinish = {},
                    )
                }),
            )
        },
        modifier = modifier,
        attachments = attachments,
    )
}

/** The 3-button navigation bar's room, which a shot has no insets for. */
@Composable
fun NavBar() = Box(Modifier.fillMaxWidth().height(48.dp))

/** A sheet where it sits on a real screen: over a dimmed home, above the navigation bar; a centred card on a wide one. */
@Composable
fun OverDimmed(sheet: @Composable BoxScope.() -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) { SampleHome() }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (RelayTheme.colors.isDark) 0.5f else 0.16f)))
        if (wide) Box(Modifier.align(Alignment.Center).widthIn(max = 480.dp), content = sheet)
        else Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp)) {
            Box(Modifier.padding(bottom = 8.dp), content = sheet)
            NavBar()
        }
    }
}
