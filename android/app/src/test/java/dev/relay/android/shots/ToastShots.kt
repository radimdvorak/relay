package dev.relay.android.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.features.home.StatusNews
import dev.relay.android.features.home.StatusToastHost
import dev.relay.android.ui.KeepToastsAbove
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.ToastHost
import dev.relay.android.ui.ToastState
import dev.relay.android.ui.Tone
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayPaperDark
import dev.relay.android.ui.theme.RelayPaperLight
import dev.relay.android.ui.theme.RelayTheme
import org.junit.Test

/** Every toast the app says, on their own and over the Threads tab. */
class ToastShots : ShotTest() {
    private val themes = mapOf("desktop" to desktopDark, "dark" to RelayPaperDark, "light" to RelayPaperLight)

    @Test
    fun messages() = inEveryTheme("toasts-messages") {
        Column(Modifier.fillMaxSize().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Said { show("Settled", "Fix the composer jump", actionLabel = "Undo", onAction = {}) }
            Said { show("Snoozed until tomorrow 9:00", "Draft the changelog", actionLabel = "Undo", onAction = {}) }
            Said { show("Copied") }
            Said { show("Scroll logs saved", "Included when you report a problem.") }
            Said { show("Couldn't change it", "Not connected to your computer.", tone = Tone.Destructive) }
            Said { show("Pushed to origin", "android-expressive · 3 commits", tone = Tone.Success) }
            Said {
                show("Settled", "Compare page copy")
                show("Settled", "Bump Compose to 1.13")
                show("Settled", "Fix the composer jump", actionLabel = "Undo", onAction = {})
            }
        }
    }

    @Test
    fun news() = inEveryTheme("toasts-news") {
        Column(Modifier.fillMaxSize().padding(vertical = 24.dp)) {
            for (kind in StatusNews.Kind.entries) StatusToastHost(StatusNews("a", "Add dark mode toggle", kind), Modifier, {}, {})
            StatusToastHost(
                StatusNews("b", "Why does the composer jump on rotate when the keyboard is up?", StatusNews.Kind.NeedsYou),
                Modifier, {}, {},
            )
        }
    }

    @Test
    fun overThreads() {
        Shot("toasts-over-message", dark = true) { OverThreads(message = true, news = false) }
        Shot("toasts-over-news", dark = true) { OverThreads(message = false, news = true) }
        Shot("toasts-over-both", dark = true) { OverThreads(message = true, news = true) }
    }

    private fun inEveryTheme(name: String, content: @Composable BoxScope.() -> Unit) {
        for ((theme, colors) in themes) Shot("$name-$theme", dark = colors.isDark) { On(colors, content) }
    }

    @Composable
    private fun On(colors: RelayColors, content: @Composable BoxScope.() -> Unit) = RelayTheme(colors) {
        WithToasts { Box(Modifier.fillMaxSize().background(RelayTheme.colors.surface)) { content() } }
    }

    @Composable
    private fun Said(say: ToastState.() -> Unit) = ToastHost(remember { ToastState().apply(say) })

    @Composable
    private fun OverThreads(message: Boolean, news: Boolean) = On(desktopDark) {
        val toasts = remember {
            ToastState().apply {
                if (message) show("Settled", "Fix the composer jump", actionLabel = "Undo", onAction = {})
            }
        }
        CompositionLocalProvider(LocalToasts provides toasts) {
            KeepToastsAbove(125.dp)
            SampleThreadsTab(busy)
            ToastHost(toasts, Modifier.align(Alignment.BottomCenter))
            val needsYou = StatusNews("2", "Why does the composer jump on rotate?", StatusNews.Kind.NeedsYou)
            if (news) StatusToastHost(needsYou, Modifier.align(Alignment.BottomEnd).fillMaxWidth(), {}, {})
        }
    }
}
