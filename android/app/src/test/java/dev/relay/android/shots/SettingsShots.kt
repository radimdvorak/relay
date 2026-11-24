package dev.relay.android.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.relay.android.features.applock.AppLock
import dev.relay.android.features.notifications.ThreadNotifications
import dev.relay.android.features.push.Push
import dev.relay.android.features.settings.LicencesScreen
import dev.relay.android.features.settings.SettingsScreen
import dev.relay.android.remote.model.RemoteOverview
import dev.relay.android.ui.LocalPaneToggle
import dev.relay.android.ui.LocalScreenInsets
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.theme.DEFAULT_CONTRAST
import dev.relay.android.ui.theme.RelayTheme
import dev.relay.android.ui.theme.ThemeMode
import dev.relay.android.ui.theme.ThemeSettings
import dev.relay.android.ui.theme.resolveColors
import dev.relay.android.ui.theme.withContrast
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.LayoutSidebar
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Settings on sample computers, in every theme it can pick. */
class SettingsShots : ShotTest() {
    private class SampleTheme(mode: ThemeMode, contrast: Int) : ThemeSettings {
        override var themeMode by mutableStateOf(mode)
            private set
        override var contrast by mutableIntStateOf(contrast)

        override fun setTheme(mode: ThemeMode) {
            themeMode = mode
        }
    }

    private fun overview(version: String = "0.30.0") =
        RemoteOverview(name = "Studio", projects = emptyList(), chats = emptyList(), version = version, bridge = 999, appearance = RelayDesktop)

    @Composable
    private fun Settings(sample: SampleApp, theme: ThemeSettings, dark: Boolean, canGoBack: Boolean = true, locked: Boolean = false) {
        val colors = resolveColors(theme.themeMode, SampleDesktop, dark)
        val lock = androidx.compose.runtime.remember {
            AppLock(File(sample.dir, "lock.bin"), PlainBox).also { if (locked) runBlocking { it.turnOn("123456", biometrics = false) } }
        }
        val notifications = androidx.compose.runtime.remember { ThreadNotifications(sample.app, sample.remote, sample.scope, MutableStateFlow(false)) }
        val push = androidx.compose.runtime.remember { Push(sample.app, sample.remote, sample.scope) }
        RelayTheme(colors.withContrast(theme.contrast)) {
            SampleShell(canGoBack) {
                SettingsScreen(sample.remote, sample.updates, notifications, push, lock, theme, sample.scope, stopRelay = {})
            }
        }
    }

    private fun sample(newest: String? = null) =
        SampleApp(listOf("Studio", "Jane's MacBook Pro in the living room", "linux-box"), overview(), newest)

    @Test
    fun top() {
        for (device in ShotDevice.entries) for (dark in listOf(false, true)) {
            val sample = sample()
            val theme = SampleTheme(if (dark) ThemeMode.Dark else ThemeMode.Light, DEFAULT_CONTRAST)
            Shot("settings", device, dark) { Settings(sample, theme, dark) }
        }
    }

    /** The user's own: the desktop's palette, dark. */
    @Test
    fun computer() {
        for (device in ShotDevice.entries) for (dark in listOf(false, true)) {
            val sample = sample()
            Shot("settings-computer", device, dark) { Settings(sample, SampleTheme(ThemeMode.Computer, DEFAULT_CONTRAST), dark) }
        }
    }

    @Test
    fun bars() {
        val sample = sample()
        Shot("settings-bars", dark = true, act = {
            systemBars()
            compose.onNode(hasScrollToIndexAction()).performTouchInput { repeat(4) { swipeUp(durationMillis = 200) } }
            compose.mainClock.advanceTimeBy(2_000)
        }) { Settings(sample, SampleTheme(ThemeMode.Computer, DEFAULT_CONTRAST), dark = true) }
    }

    @Test
    fun rest() {
        for (dark in listOf(false, true)) for ((part, index) in listOf("middle" to 2, "computers" to 5, "end" to 7)) {
            val sample = sample("99.0.0")
            val theme = SampleTheme(ThemeMode.Computer, DEFAULT_CONTRAST)
            Shot(
                "settings-$part", dark = dark,
                act = { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index) },
            ) { Settings(sample, theme, dark, locked = true) }
        }
    }

    @Test
    fun contrast() {
        for (dark in listOf(false, true)) for (level in listOf(0, 22, 100)) {
            val sample = sample()
            val theme = SampleTheme(ThemeMode.Phone, level)
            Shot("settings-contrast$level", dark = dark) { Settings(sample, theme, dark) }
        }
    }

    @Test
    fun bigText() {
        val sample = sample()
        val theme = SampleTheme(ThemeMode.Computer, 61)
        for ((part, index) in listOf("top" to 0, "computers" to 5)) Shot(
            "settings-big-$part",
            act = { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index) },
        ) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) { Settings(sample, theme, dark = false) }
        }
    }

    /** The Fold's pane: the sidebar's button in the header, no ← on its first screen. */
    @Test
    fun pane() {
        for (dark in listOf(false, true)) {
            val sample = sample()
            val theme = SampleTheme(ThemeMode.Phone, DEFAULT_CONTRAST)
            Shot("settings-pane", ShotDevice.FoldInner, dark) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(280.dp).fillMaxHeight().background(RelayTheme.colors.chrome))
                    CompositionLocalProvider(
                        LocalScreenInsets provides WindowInsetsSides.End + WindowInsetsSides.Top + WindowInsetsSides.Bottom,
                        LocalPaneToggle provides { CircleButton(TablerIcons.Outline.LayoutSidebar, "Hide sidebar", {}, tone = CircleTone.Raised) },
                    ) {
                        Box(Modifier.weight(1f)) { Settings(sample, theme, dark, canGoBack = false) }
                    }
                }
            }
        }
    }

    @Test
    fun licences() {
        for (dark in listOf(false, true)) Shot("licences", dark = dark, act = { Thread.sleep(800); Snapshot.sendApplyNotifications() }) { SampleShell { LicencesScreen() } }
    }

    @Test
    fun themeStrip() {
        val sample = sample()
        val theme = SampleTheme(ThemeMode.Light, DEFAULT_CONTRAST)
        Strip("strip-theme", frames = 8, start = { compose.onNodeWithContentDescription("Dark", substring = true).performClick() }) {
            Settings(sample, theme, dark = false)
        }
    }

    @Test
    fun contrastStrip() {
        val sample = sample()
        val theme = SampleTheme(ThemeMode.Light, DEFAULT_CONTRAST)
        Strip("strip-contrast-tap", frames = 12, start = {
            compose.onNodeWithContentDescription("Contrast").performTouchInput { down(centerRight.copy(x = right - 60f)); up() }
        }) { Settings(sample, theme, dark = false) }
        Strip("strip-contrast-reset", frames = 12, start = { compose.onNodeWithText("Reset").performClick() }) {
            Settings(sample, theme, dark = false)
        }
    }

    @Test
    fun contrastDrag() {
        val sample = sample()
        val theme = SampleTheme(ThemeMode.Light, DEFAULT_CONTRAST)
        var at = 0
        Strip("strip-contrast-drag", frames = 10, each = {
            compose.onNodeWithContentDescription("Contrast").performTouchInput {
                if (at++ == 0) down(center) else moveBy(androidx.compose.ui.geometry.Offset(40f, 0f))
            }
        }) { Settings(sample, theme, dark = false) }
        compose.onNodeWithContentDescription("Contrast").performTouchInput { up() }
    }
}
