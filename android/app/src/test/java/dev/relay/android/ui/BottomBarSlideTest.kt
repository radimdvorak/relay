package dev.relay.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.relay.android.ui.theme.RelayTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Sliding along the bar picks the tab let go on, once; taps and everything else stay as they were. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-mdpi")
class BottomBarSlideTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var tab by mutableStateOf("Threads")
    private val picks = mutableListOf<String>()

    @Before
    fun show() {
        val tabs = listOf("Threads" to BarGlyph.Threads, "Projects" to BarGlyph.Projects, "PRs" to BarGlyph.Pulls)
        compose.setContent {
            RelayTheme {
                Box(Modifier.width(360.dp).testTag("bar")) {
                    BottomBar(tabs.map { (name, glyph) -> BarTab(name, glyph, name) }, tab, { picks += it; tab = it })
                }
            }
        }
    }

    private fun touch(gesture: TouchInjectionScope.() -> Unit) {
        compose.onNodeWithTag("bar").performTouchInput(gesture)
        compose.waitForIdle()
    }

    /** Slow enough that its speed means nothing. */
    private fun TouchInjectionScope.slideTo(x: Float, from: Float = 60f) {
        repeat(30) { i -> moveTo(Offset(from + (x - from) * (i + 1) / 30f, centerY), delayMillis = 16) }
        repeat(10) { move(delayMillis = 16) }
    }

    private fun TouchInjectionScope.press(x: Float = 60f) = down(Offset(x, centerY))

    @Test
    fun slidingToTheEndPicksOnlyTheLastTab() {
        touch { press(); slideTo(300f); up() }
        assertEquals(listOf("PRs"), picks)
        compose.onNodeWithContentDescription("PRs").assertIsSelected()
    }

    @Test
    fun itPicksNothingBeforeLettingGo() {
        touch { press(); slideTo(300f) }
        assertEquals(emptyList<String>(), picks)
        compose.onNodeWithContentDescription("Threads").assertIsSelected()
        touch { up() }
        assertEquals(listOf("PRs"), picks)
    }

    @Test
    fun slidingBackStaysWhereItWas() {
        touch { press(); slideTo(300f); slideTo(60f, from = 300f); up() }
        assertEquals(emptyList<String>(), picks)
    }

    @Test
    fun aSlideCanStartOnAnyTab() {
        touch { press(180f); slideTo(300f, from = 180f); up() }
        assertEquals(listOf("PRs"), picks)
    }

    @Test
    fun holdingFirstStillSlides() {
        touch { press(); advanceEventTime(800); slideTo(180f); up() }
        assertEquals(listOf("Projects"), picks)
    }

    @Test
    fun aCancelledSlidePicksNothing() {
        touch { press(); slideTo(300f); cancel() }
        assertEquals(emptyList<String>(), picks)
        touch { click(Offset(180f, centerY)) }
        assertEquals(listOf("Projects"), picks)
    }

    @Test
    fun aTapStillPicksOnce() {
        touch { click(Offset(180f, centerY)) }
        assertEquals(listOf("Projects"), picks)
    }

    @Test
    fun aFlickCarriesToTheNextTab() {
        assertEquals(1, barTabFor(x = 100f, speed = 600f, width = 360f, tabs = 3))
        assertEquals(0, barTabFor(x = 100f, speed = 0f, width = 360f, tabs = 3))
    }

    @Test
    fun pastTheEndsThePillOnlyGives() {
        assertEquals(1f, barPosition(180f, 360f, 3), 0.001f)
        assertEquals(-0.175f, barPosition(0f, 360f, 3), 0.001f)
        assertEquals(2.175f, barPosition(360f, 360f, 3), 0.001f)
    }
}
