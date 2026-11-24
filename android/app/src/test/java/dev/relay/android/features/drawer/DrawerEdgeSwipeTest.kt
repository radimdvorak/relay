package dev.relay.android.features.drawer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.relay.android.features.home.SwipeAction
import dev.relay.android.features.home.SwipeTriage
import dev.relay.android.ui.theme.RelayTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** One swipe to the right means one thing: the drawer near the edge, a row's triage further in, nothing at the very edge. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-mdpi")
class DrawerEdgeSwipeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var drawer: DrawerState
    private var settled = 0

    @Before
    fun show() {
        compose.setContent {
            RelayTheme {
                drawer = rememberDrawerState()
                Box(Modifier.fillMaxSize().drawerEdgeSwipe(drawer, enabled = true, screenWidth = 412.dp)) {
                    SwipeTriage(SwipeAction("Settle") { settled++; true }, null, blocked = false) {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    private fun swipeRight(from: Float, dy: Float = 0f) {
        compose.onRoot().performTouchInput { swipe(Offset(from, 300f), Offset(from + 250f, 300f + dy), durationMillis = 300) }
        compose.waitForIdle()
    }

    @Test
    fun nearTheEdgeItOpensTheDrawerAndTheRowStaysPut() {
        swipeRight(from = 34f)
        assertEquals(1f, drawer.progress)
        assertEquals(0, settled)
    }

    @Test
    fun furtherInItIsTheRows() {
        swipeRight(from = 90f)
        assertEquals(0f, drawer.progress)
        assertEquals(1, settled)
    }

    @Test
    fun theVeryEdgeIsNotTheDrawers() {
        swipeRight(from = 10f)
        assertEquals(0f, drawer.progress)
    }

    @Test
    fun scrollingNearTheEdgeOpensNothing() {
        swipeRight(from = 34f, dy = 500f)
        assertEquals(0f, drawer.progress)
        assertEquals(0, settled)
    }
}
