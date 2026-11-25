package dev.relay.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.relay.android.ui.theme.RelayPaperDark
import dev.relay.android.ui.theme.RelayTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ToastTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val toasts = ToastState()

    @Test
    fun aToastStaysAboveADockThatGrowsUnderIt() {
        var dock by mutableStateOf(60.dp)
        show { dock }
        toasts.show("Copied")
        compose.onNodeWithText("Copied").assertIsDisplayed()
        assertToastClearsDock()

        dock = 300.dp
        assertToastClearsDock()
    }

    @Test
    fun aToastAlreadyUpMovesOffADockThatArrives() {
        toasts.show("Copied")
        show { 60.dp }
        assertToastClearsDock()
    }

    /** The leaving screen lets go after the arriving one has claimed, which mustn't drop the newer claim. */
    @Test
    fun aScreenLeavingKeepsTheNewOnesRoom() {
        val leaving = ToastClearance().apply { height = 120.dp }
        val arriving = ToastClearance().apply { height = 90.dp }
        toasts.claim(leaving)
        toasts.claim(arriving)
        toasts.release(leaving)
        assertEquals(90.dp, toasts.clearance)

        toasts.release(arriving)
        assertEquals(0.dp, toasts.clearance)
    }

    @Test
    fun overAThreadOnlyWhatCanBeUndoneStaysAtTheBottom() {
        toasts.plainEdge = ToastEdge.Top
        toasts.show("Couldn't stop it")
        toasts.undo("Settled") {}
        assertEquals(listOf(ToastEdge.Top, ToastEdge.Bottom), toasts.toasts.map { it.edge })
    }

    @Test
    fun aToastAtTheTopIsSwipedUpAndNotDown() {
        toasts.plainEdge = ToastEdge.Top
        toasts.show("Couldn't stop it", durationMs = 60_000)
        compose.mainClock.autoAdvance = false
        compose.setContent { RelayTheme(RelayPaperDark) { ToastHost(toasts, ToastEdge.Top) } }
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithText("Couldn't stop it").performTouchInput { swipeDown() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1, toasts.toasts.size)

        compose.onNodeWithText("Couldn't stop it").performTouchInput { swipeUp(startY = bottom, endY = top - 300f) }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0, toasts.toasts.size)
    }

    private fun show(dock: () -> Dp) = compose.setContent {
        RelayTheme(RelayPaperDark) {
            CompositionLocalProvider(LocalToasts provides toasts) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.BottomCenter).testTag("dock").keepsToastsAbove().fillMaxWidth().height(dock()))
                    ToastHost(toasts, ToastEdge.Bottom, Modifier.align(Alignment.BottomEnd))
                }
            }
        }
    }

    private fun assertToastClearsDock() {
        compose.waitForIdle()
        val toast = compose.onNodeWithText("Copied").getUnclippedBoundsInRoot()
        val dock = compose.onNodeWithTag("dock").getUnclippedBoundsInRoot()
        assertTrue("toast ends at ${toast.bottom}, dock starts at ${dock.top}", toast.bottom <= dock.top)
    }
}
