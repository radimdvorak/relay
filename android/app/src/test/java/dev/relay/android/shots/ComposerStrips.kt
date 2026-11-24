package dev.relay.android.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.relay.android.features.composer.SendState
import dev.relay.android.ui.theme.RelayTheme
import java.io.File
import org.junit.Test

/** The composer's movements frame by frame: `build/shots/strip-<name>_<frame>.png`, 33ms apart. */
class ComposerStrips : ShotTest() {
    private fun strip(name: String, frames: Int = 12, perFrame: (Int) -> Unit = {}, act: () -> Unit, content: @Composable () -> Unit) {
        Shot("strip-$name") { content() }
        act()
        for (i in 0 until frames) {
            perFrame(i)
            compose.mainClock.advanceTimeBy(33)
            val file = File(System.getProperty("relay.shots.dir") ?: "build/shots", "strip-${name}_%02d.png".format(i))
            compose.onRoot().captureRoboImage(file.path)
        }
    }

    private fun box(name: String, from: Pair<SendState, Boolean>, to: Pair<SendState, Boolean>, text: String = "") {
        var now by mutableStateOf(from)
        strip(name, act = { now = to }) {
            Docked { SampleBox(now.first, now.second, if (now == to) text else "", voice = listOf(0.3f, 0.8f, 0.4f, 0.9f, 0.7f)) }
        }
    }

    @Test
    fun expand() = box("expand", SendState(empty = true) to false, SendState(empty = true) to true)

    @Test
    fun collapse() = box("collapse", SendState(empty = true) to true, SendState(empty = true) to false)

    @Test
    fun micToSend() = box("mic-to-send", SendState(empty = true) to true, SendState(empty = false) to true, "H")

    @Test
    fun sendToStop() = box(
        "send-to-stop", SendState(empty = false) to true, SendState(empty = true, running = true, stop = true) to false,
    )

    @Test
    fun dictate() = box(
        "dictate", SendState(empty = true) to false, SendState(empty = true, dictating = true, discardable = true) to true,
    )

    @Test
    fun wheel() = strip("wheel", frames = 16, act = {
        compose.onNode(hasText("Opus 5.5") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performTouchInput {
            down(center)
            moveBy(Offset(0f, -60f))
            moveBy(Offset(0f, -60f))
            moveBy(Offset(0f, -40f))
            advanceEventTime(60)
            up()
        }
    }) { OverDimmed { Models("claude", claudeRows(), "opus") } }

    @Test
    fun wheelTap() = strip("wheel-tap", frames = 16, act = {
        compose.onNode(hasText("Haiku 5.5")).performTouchInput { down(center); up() }
    }) { OverDimmed { Models("claude", claudeRows(), "opus") } }

    @Test
    fun effort() = strip("effort", frames = 16, act = {
        compose.onNode(hasContentDescription("Effort")).performTouchInput { down(Offset(width * 0.35f, centerY)) }
    }, perFrame = { i ->
        compose.onNode(hasContentDescription("Effort")).performTouchInput {
            if (i < 8) moveBy(Offset(width * 0.055f, 0f)) else if (i == 8) up()
        }
    }) { OverDimmed { Effort(listOf("low", "medium", "high", "xhigh", "max"), "medium", "") } }
}

@Composable
private fun Docked(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(RelayTheme.colors.surface), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.padding(15.dp)) { content() }
    }
}
