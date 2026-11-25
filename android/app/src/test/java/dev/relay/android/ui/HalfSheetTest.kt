package dev.relay.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.relay.android.ui.theme.RelayTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

private const val HEIGHT = 915
private const val NAVIGATION_BAR = 48
private const val ROW = 20

/** A sheet that opens at half height, over a 3-button navigation bar. One pixel is one dp. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w412dp-h${HEIGHT}dp-mdpi")
class HalfSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val tapped = mutableListOf<Int>()

    @Test
    fun itEndsAboveTheNavigationBar() {
        open()
        tap(HEIGHT - NAVIGATION_BAR / 2)
        assertEquals(emptyList<Int>(), tapped)

        tap(HEIGHT - NAVIGATION_BAR - 40)
        assertEquals(1, tapped.size)
    }

    @Test
    fun pulledUpItShowsTheRest() {
        open()
        val atHalf = top()
        root().performTouchInput { swipe(Offset(200f, 700f), Offset(200f, 100f)) }
        compose.waitForIdle()
        assertTrue("went from $atHalf to ${top()}", top() < atHalf)

        // Far below the rows half the sheet had room for.
        tap(HEIGHT - NAVIGATION_BAR - 40)
        assertTrue("tapped $tapped", tapped.single() > (HEIGHT / 2) / ROW)
    }

    private fun root() = compose.onNode(isRoot() and hasAnyDescendant(hasTestTag("sheet")))

    private fun top() = compose.onNodeWithTag("sheet").getUnclippedBoundsInRoot().top.value

    private fun tap(y: Int) {
        root().performTouchInput { click(Offset(200f, y.toFloat())) }
        compose.waitForIdle()
    }

    private fun open() {
        compose.setContent {
            RelayTheme {
                InsetModalSheet({}, halfFirst = true) {
                    Column(Modifier.testTag("sheet")) {
                        repeat(36) { row ->
                            Box(Modifier.clickable { tapped += row }.fillMaxWidth().height(ROW.dp))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val bars = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAVIGATION_BAR))
                .build()
            ViewCompat.dispatchApplyWindowInsets(ShadowDialog.getLatestDialog().window!!.decorView, bars)
        }
        compose.waitForIdle()
    }
}
