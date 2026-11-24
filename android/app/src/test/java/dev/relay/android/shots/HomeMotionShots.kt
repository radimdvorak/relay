package dev.relay.android.shots

import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.relay.android.features.home.StatusNews
import dev.relay.android.features.home.StatusToastHost
import dev.relay.android.lib.navigation.NEW_THREAD_MORPH
import dev.relay.android.ui.LocalScreenAnimation
import dev.relay.android.ui.LocalSharedTransitions
import dev.relay.android.ui.sharedBox
import dev.relay.android.ui.theme.RelayPaperDark
import dev.relay.android.ui.theme.RelayPaperLight
import dev.relay.android.ui.theme.RelayTheme
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import java.io.File

/** Home's motion a frame at a time: 33ms apart, one PNG each. */
class HomeMotionShots : ShotTest() {
    private fun strip(name: String, frames: Int, dark: Boolean = false, start: () -> Unit, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("${ShotDevice.Phone.size}-${if (dark) "night" else "notnight"}-xhdpi")
        compose.mainClock.autoAdvance = false
        compose.activity.setContent {
            RelayTheme(if (dark) RelayPaperDark else RelayPaperLight) {
                Box(Modifier.fillMaxSize().background(RelayTheme.colors.surface)) { WithToasts { content() } }
            }
        }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(500)
        start()
        Snapshot.sendApplyNotifications()
        val dir = File(System.getProperty("relay.shots.dir") ?: "build/shots")
        repeat(frames) { i ->
            compose.onRoot().captureRoboImage(File(dir, "strip-$name-${"%02d".format(i)}.png").path)
            compose.mainClock.advanceTimeBy(33)
        }
    }

    @Test
    fun morph() {
        var open by mutableStateOf(false)
        strip("morph", 14, start = { open = true }) { Morph(open) }
    }

    @Test
    fun morphBack() {
        var open by mutableStateOf(true)
        strip("morph-back", 14, start = { open = false }) { Morph(open) }
    }

    /** The two screens crossfading while the greeting and the composer travel. */
    @Composable
    private fun Morph(open: Boolean) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitions provides this) {
                AnimatedContent(
                    open,
                    transitionSpec = {
                        fadeIn(RelayTheme.motion.standard.defaultEffectsSpec()) togetherWith
                            fadeOut(RelayTheme.motion.standard.defaultEffectsSpec())
                    },
                ) { opened ->
                    CompositionLocalProvider(LocalScreenAnimation provides this) {
                        if (opened) SampleNewThread {
                            SampleComposer(true, null, Modifier.sharedBox(NEW_THREAD_MORPH, RelayTheme.shapes.composer))
                        } else SampleThreadsTab()
                    }
                }
            }
        }
    }

    @Test
    fun toast() {
        var shown by mutableStateOf<StatusNews?>(null)
        strip("toast", 10, start = { shown = StatusNews("a", "Add dark mode toggle", StatusNews.Kind.Finished) }) { Toasts(shown) }
    }

    @Test
    fun toastGivesWay() {
        var shown by mutableStateOf<StatusNews?>(StatusNews("a", "Add dark mode toggle", StatusNews.Kind.Finished))
        strip("toast-next", 10, dark = true, start = {
            shown = StatusNews("b", "Why does the composer jump on rotate when the keyboard is up?", StatusNews.Kind.NeedsYou)
        }) { Toasts(shown) }
    }

    @Composable
    private fun Toasts(shown: StatusNews?) {
        SampleThreadsTab()
        Box(Modifier.fillMaxSize().padding(bottom = 125.dp), contentAlignment = Alignment.BottomEnd) {
            StatusToastHost(shown, Modifier.fillMaxWidth(), {}, {})
        }
    }
}
