package dev.relay.android.features.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Leaving the Threads tab takes its composer away for taps at once, not once it has finished leaving. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-mdpi")
class HomeDockTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var tab by mutableStateOf(HomeTab.Threads)
    private var threadsStarted = 0
    private var rowsTapped = 0
    private var composer = Offset.Unspecified

    @Before
    fun show() {
        compose.setContent {
            RelayTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().pressable({ rowsTapped++ }))
                    HomeDock(
                        tab, { tab = it },
                        followsKeyboard = true,
                        threadsNews = false,
                        onHeight = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                        hasComposer = tab == HomeTab.Threads,
                        composer = { HomeComposer(onOpen = { threadsStarted++ }) },
                    )
                }
            }
        }
        val box = compose.onNodeWithContentDescription("New thread").getBoundsInRoot()
        composer = Offset((box.left + box.right).value / 2, (box.top + box.bottom).value / 2)
    }

    private fun tapComposerAfter(ms: Long) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            tab = HomeTab.Projects
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(ms)
        compose.onRoot().performTouchInput { click(composer) }
        compose.mainClock.advanceTimeBy(2000)
    }

    private fun assertReachedThePage() {
        assertEquals("threads started", 0, threadsStarted)
        assertEquals("rows tapped", 1, rowsTapped)
    }

    @Test
    fun onThreadsItStartsAThread() {
        compose.onRoot().performTouchInput { click(composer) }
        compose.waitForIdle()
        assertEquals(1, threadsStarted)
    }

    @Test
    fun rightAfterLeavingATapReachesThePage() {
        tapComposerAfter(50)
        assertReachedThePage()
    }

    @Test
    fun whileItShrinksATapReachesThePage() {
        tapComposerAfter(200)
        assertReachedThePage()
    }

    @Test
    fun onceGoneATapReachesThePage() {
        tapComposerAfter(3000)
        assertReachedThePage()
    }
}
