package dev.relay.android.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.relay.android.features.pairing.PairScreen
import dev.relay.android.ui.theme.RelayPaperDark
import dev.relay.android.ui.theme.RelayTheme
import java.io.File
import org.junit.Test

/** Pairing around the field, which the JVM has no GL to draw. */
class PairingShots : ShotTest() {
    private val link = "relay-remote://pair?h=192.0.2.10&p=4711&k=${"k".repeat(43)}&c=${"c".repeat(32)}&n=Studio"

    @Composable
    private fun Pairing(sample: SampleApp, link: String? = null) = SampleShell {
        RelayTheme(RelayPaperDark.copy(surface = Color.Black)) {
            // The field paints this itself on a device; the JVM has no GL.
            Box(Modifier.background(Color.Black)) { PairScreen(sample.remote, sample.updates, link, stopRelay = {}) }
        }
    }

    @Test
    fun firstRun() {
        for (device in ShotDevice.entries) {
            val sample = SampleApp()
            Shot("pairing-first", device, dark = true) { Pairing(sample) }
        }
    }

    @Test
    fun largeType() {
        val sample = SampleApp()
        Shot("pairing-large-type", dark = true) { WithToasts(fontScale = 1.6f) { Pairing(sample) } }
    }

    @Test
    fun another() {
        val sample = SampleApp(listOf("Studio"))
        Shot("pairing-another", dark = true) { Pairing(sample) }
    }

    @Test
    fun updateOffered() {
        val sample = SampleApp(newest = "99.0.0")
        Shot("pairing-update", dark = true) { Pairing(sample) }
    }

    @Test
    fun linked() {
        val sample = SampleApp()
        Shot("pairing-link", dark = true) { Pairing(sample, link) }
    }

    @Test
    fun pairing() {
        val sample = SampleApp()
        Shot("pairing-waiting", dark = true, act = { compose.onNodeWithText("Pair").performClick() }) { Pairing(sample, link) }
    }

    @Test
    fun pasting() {
        val sample = SampleApp()
        compose.mainClock.autoAdvance = false
        Shot("pairing-behind-paste", dark = true) { Pairing(sample) }
        compose.onNodeWithText("Paste link").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNode(isDialog()).captureRoboImage(File(System.getProperty("relay.shots.dir"), "pairing-paste.png").path)
        compose.onNodeWithText("Cancel").assertExists()
    }
}
