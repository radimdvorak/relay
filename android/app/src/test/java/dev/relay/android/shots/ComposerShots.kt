package dev.relay.android.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.relay.android.features.composer.AddCommands
import dev.relay.android.features.composer.AddContent
import dev.relay.android.features.composer.AttachActions
import dev.relay.android.features.composer.CommandMenu
import dev.relay.android.features.composer.ImageStrip
import dev.relay.android.features.composer.ModelChoices
import dev.relay.android.features.composer.PickedFile
import dev.relay.android.features.composer.SendState
import dev.relay.android.features.composer.ThreadOptions
import dev.relay.android.features.composer.UsageBar
import dev.relay.android.features.composer.UsageContent
import dev.relay.android.features.composer.commandItems
import dev.relay.android.features.composer.logic.FilePhase
import dev.relay.android.features.composer.logic.ProviderUsage
import dev.relay.android.features.composer.logic.UsageWindow
import dev.relay.android.features.sketch.Ink
import dev.relay.android.features.sketch.SketchScreen
import dev.relay.android.features.sketch.StrokeHistory
import dev.relay.android.features.sketch.penColors
import dev.relay.android.remote.model.ContextUsage
import dev.relay.android.remote.model.ModelChoice
import dev.relay.android.remote.model.ModelCost
import dev.relay.android.remote.model.ResumeSettings
import dev.relay.android.remote.model.ThreadCost
import dev.relay.android.ui.RelaySheetFrame
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import org.junit.Test

/** The composer in each of its states, and the sheets it opens. */
class ComposerShots : ShotTest() {
    /** Both palettes on the phone, then the user's desktop one, then the Fold. */
    private fun everywhere(name: String, content: @Composable () -> Unit) {
        for (dark in listOf(false, true)) Shot(name, dark = dark) { content() }
        Shot("$name-desktop", dark = true) { RelayTheme(desktopDark) { Surface { content() } } }
        Shot(name, ShotDevice.FoldInner) { content() }
    }

    @Test
    fun boxes() = everywhere("composer-boxes") { Boxes() }

    @Test
    fun boxesLargeText() = Shot("composer-boxes-large") {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) { Boxes() }
    }

    @Test
    fun add() = everywhere("composer-add") {
        OverDimmed {
            RelaySheetFrame("Add", {}) {
                SheetColumn {
                    AddContent(
                        AttachActions({}, {}, {}, {}), AddCommands("Commands", "Relay's and the agent's, or type /") {}, {},
                        options = { Options(null) },
                    )
                }
            }
        }
    }

    @Test
    fun threadSettings() = Shot("composer-settings") {
        OverDimmed { RelaySheetFrame("Thread settings", {}) { SheetColumn { Options("Opus 5.5 · High") } } }
    }

    @Test
    fun usage() = everywhere("composer-usage") {
        OverDimmed {
            RelaySheetFrame("Claude usage", {}) {
                SheetColumn { UsageContent("claude", plan, ContextUsage(168_000, 200_000, 4_300_000), cost, {}, {}) }
            }
        }
    }

    @Test
    fun commands() = everywhere("composer-commands") {
        Column(Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.Bottom)) {
            CommandMenu(commandItems("/", "claude", emptyList()).orEmpty(), loading = true, failed = false, onPick = {})
            SampleBox(SendState(empty = false), expanded = true, text = "/")
            NavBar()
        }
    }

    @Test
    fun sketch() {
        for (device in ShotDevice.entries) Shot("composer-sketch", device) {
            SketchScreen(StrokeHistory(emptyList()), Ink.Pen, penColors[4], false, {}, {}, {}, {}, {}, {}) {
                Text("photo", color = RelayTheme.colors.inkFaint)
            }
        }
    }
}

@Composable
private fun Surface(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(RelayTheme.colors.surface)) { content() }
}

@Composable
private fun SheetColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

private val settings = ResumeSettings("claude", ModelChoice("opus"), null, "auto-accept-edits", "plan")
private const val HOUR = 3_600_000L
private val plan
    get() = System.currentTimeMillis().let { now ->
        ProviderUsage(
            "claude",
            listOf(
                UsageWindow("session", 34.0, now + 2 * HOUR, 5 * HOUR),
                UsageWindow("weekly", 91.0, now + 80 * HOUR, 168 * HOUR),
                UsageWindow("weekly", 48.0, now + 80 * HOUR, 168 * HOUR, label = "Weekly Opus", model = "opus"),
            ),
        )
    }
private val cost = ThreadCost(3.42, 18, listOf(ModelCost("Opus 5.5", 3.10), ModelCost("Haiku 5.5", 0.32)))

@Composable
private fun Options(model: String?) {
    ThreadOptions(settings, model, {}, {}, {}, usage = { UsageBar("claude", plan, ContextUsage(168_000, 200_000), cost, {}) })
}

@Composable
private fun Boxes() {
    val file = { key: String, name: String, phase: FilePhase -> PickedFile(key, name, key == "v", phase, size = 4_200_000L) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SampleBox(SendState(empty = true), expanded = false)
        SampleBox(SendState(empty = true, running = true, stop = true), expanded = false)
        SampleBox(SendState(empty = true, enabled = false), expanded = false, mic = false, placeholder = "What should we work on?")
        SampleBox(SendState(empty = true), expanded = true)
        SampleBox(SendState(empty = false), expanded = true, text = "Make the composer sit on the keyboard", choices = ModelChoices("codex", "GPT-6-Sol", "Extra", fast = true, plan = true))
        SampleBox(
            SendState(empty = false, running = true, steering = true), expanded = true, text = "Actually, keep the old one",
            choices = ModelChoices("opencode", "anthropic/claude-opus-5-5-preview", null),
        )
        SampleBox(
            SendState(empty = false, dictating = true, discardable = true), expanded = true,
            text = "Words come in here as they're heard, and settle as the engine makes up its mind about them",
            voice = listOf(0.2f, 0.5f, 0.9f, 0.4f, 0.8f),
        )
        SampleBox(
            SendState(empty = false), expanded = true, text = "What's wrong here?",
            attachments = {
                ImageStrip(
                    emptyList(), listOf(1), {},
                    files = listOf(
                        file("a", "crash-2026-11-24-very-long-name.log", FilePhase.Ready),
                        file("v", "screen.mp4", FilePhase.Failed),
                    ),
                )
            },
        )
    }
}
