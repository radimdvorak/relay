package dev.relay.android.shots.composer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.relay.android.features.composer.EffortPage
import dev.relay.android.features.composer.ModelPage
import dev.relay.android.features.composer.ModelSearch
import dev.relay.android.features.composer.ModelToggle
import dev.relay.android.features.composer.logic.AgentModel
import dev.relay.android.features.composer.logic.ModelRow
import dev.relay.android.features.composer.logic.effortLevels
import dev.relay.android.features.composer.logic.modelRows
import dev.relay.android.shots.ShotDevice
import dev.relay.android.shots.ShotTest
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.RelaySheetFrame
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Search
import org.junit.Test

/** The model and effort sheets with one model, sixty, and every count of effort levels. */
class ModelSheetShots : ShotTest() {
    private fun sheet(name: String, device: ShotDevice = ShotDevice.Phone, dark: Boolean = false, content: @Composable () -> Unit) =
        Shot(name, device, dark) { OverDimmed { content() } }

    @Test
    fun claude() {
        for (device in ShotDevice.entries) for (dark in listOf(false, true)) sheet("model-claude", device, dark) { Models("claude", claudeRows(), "opus") }
        Shot("model-claude-desktop", dark = true) { RelayTheme(usersDesktop) { Column(Modifier.fillMaxSize()) { OverDimmed { Models("claude", claudeRows(), "opus") } } } }
        sheet("model-claude-toggles") {
            Models("claude", claudeRows(), "opus", toggles = listOf(ModelToggle("200k context window", "Off leaves Claude on its default window, 1M on most models.", true) {}))
        }
    }

    @Test
    fun largeText() = sheet("model-large") {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) { Models("claude", claudeRows(), "opus") }
    }

    @Test
    fun oneModel() = sheet("model-one") {
        Models("amp", modelRows("amp", emptyList(), "", null), "", agents = listOf("amp"))
    }

    @Test
    fun sixty() = sheet("model-sixty") { Models("opencode", modelRows("opencode", sixtyModels, "vendor-28/model", null), "vendor-28/model") }

    @Test
    fun loadingAndFailed() {
        sheet("model-loading") { Models("codex", null, "gpt-6-sol") }
        sheet("model-failed") { Models("codex", modelRows("codex", emptyList(), "gpt-6-sol", null), "gpt-6-sol", error = "Codex isn't signed in on your computer.") }
    }

    @Test
    fun search() {
        for (dark in listOf(false, true)) sheet("model-search", dark = dark) {
            RelaySheetFrame("Model", {}, back = true) {
                ModelSearch(
                    "opus", {}, agents,
                    mapOf("claude" to claudeModels, "opencode" to listOf(AgentModel("x", "Claude Opus 5.5 (Zen)", group = "Zen"))),
                    { provider, model -> provider == "claude" && model.id == "opus" }, { _, _ -> },
                )
            }
        }
    }

    @Test
    fun effort() {
        val five = listOf("low", "medium", "high", "xhigh", "max")
        for (dark in listOf(false, true)) sheet("effort-5", dark = dark) { Effort(five, "medium", "high") }
        Shot("effort-5-desktop", dark = true) { RelayTheme(usersDesktop) { Column(Modifier.fillMaxSize()) { OverDimmed { Effort(five, "medium", "high") } } } }
        sheet("effort-5", ShotDevice.FoldInner) { Effort(five, "medium", "") }
        sheet("effort-max", dark = true) { Effort(five, "medium", "max") }
        sheet("effort-0") { Effort(emptyList(), null, "") }
        sheet("effort-1") { Effort(listOf("high"), "high", "") }
        sheet("effort-2") { Effort(listOf("low", "high"), "low", "high") }
        sheet("effort-3") { Effort(listOf("low", "medium", "high"), "medium", "") }
        sheet("effort-6") { Effort(five + "ultra", "high", "xhigh") }
        sheet("effort-unknown-default") { Effort(listOf("low", "high"), null, "") }
    }
}

@Composable
internal fun Models(
    provider: String,
    rows: List<ModelRow>?,
    start: String,
    agents: List<String> = dev.relay.android.shots.composer.agents,
    toggles: List<ModelToggle> = emptyList(),
    error: String? = null,
) {
    var chosen by remember { mutableStateOf(start) }
    RelaySheetFrame(
        "Model", {},
        action = if (agents.size > 1) ({ CircleButton(TablerIcons.Outline.Search, "Search models", {}) }) else null,
    ) {
        ModelPage(agents, provider, {}, rows, chosen, "medium", { chosen = it }, {}, toggles = toggles, error = error)
    }
}

@Composable
internal fun Effort(levels: List<String>, default: String?, start: String) {
    var effort by remember { mutableStateOf(start) }
    RelaySheetFrame("Effort", {}, back = true) { EffortPage(effortLevels(levels, default), effort, { effort = it }) }
}
