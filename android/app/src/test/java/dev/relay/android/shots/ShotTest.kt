package dev.relay.android.shots

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.relay.android.ui.theme.HextaDark
import dev.relay.android.ui.theme.HextaLight
import dev.relay.android.ui.theme.RelayTheme
import java.io.File
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The screens a shot is drawn on. */
enum class ShotDevice(val size: String, val density: String) {
    Phone("w412dp-h915dp", "xxhdpi"),

    /** Galaxy Z Fold7 unfolded, roughly: wide enough for list-detail. */
    FoldInner("w750dp-h832dp", "420dpi"),
}

/**
 * Draws composables to PNGs on the JVM (Robolectric's native graphics, Roborazzi's
 * capture), so an agent can look at UI without a device. `scripts/shots.sh` runs
 * these and prints where the pictures went; nothing compares them against anything.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Robolectric's newest Android is older than compileSdk; the app targets 36 anyway.
@Config(sdk = [36])
abstract class ShotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** [content] in the app's theme on [device], saved as `build/shots/<name>_<device>_<light|dark>.png`. */
    @Suppress("FunctionName")
    fun Shot(name: String, device: ShotDevice = ShotDevice.Phone, dark: Boolean = false, content: @Composable () -> Unit) {
        val night = if (dark) "night" else "notnight"
        RuntimeEnvironment.setQualifiers("${device.size}-$night-${device.density}")
        // Spinners and shimmers never settle, so the clock only moves when told to.
        compose.mainClock.autoAdvance = false
        compose.activity.setContent {
            RelayTheme(if (dark) HextaDark else HextaLight) {
                Box(Modifier.fillMaxSize().background(RelayTheme.colors.background)) { content() }
            }
        }
        // Long enough for entrances and fades to finish.
        compose.mainClock.advanceTimeBy(1_000)
        val file = File(shotsDir(), "${name}_${device.name}_${if (dark) "dark" else "light"}.png")
        compose.onRoot().captureRoboImage(file.path)
        println("shot: ${file.path}")
    }

    private fun shotsDir() = File(System.getProperty("relay.shots.dir") ?: "build/shots")
}
