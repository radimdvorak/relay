package dev.relay.android.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.relay.android.ui.theme.RelayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** A sheet lies over the keyboard until it has a field to type in. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-mdpi")
class OverKeyboardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var searching by mutableStateOf(false)

    private fun show(window: @Composable () -> Unit) {
        compose.setContent { RelayTheme { window() } }
        compose.waitForIdle()
    }

    private fun coversKeyboard(): Boolean {
        compose.waitForIdle()
        val flag = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        return ShadowDialog.getLatestDialog().window!!.attributes.flags and flag != 0
    }

    private fun followsItsField(window: @Composable () -> Unit) {
        show(window)
        assertEquals(true, coversKeyboard())
        compose.runOnIdle { searching = true }
        assertEquals(false, coversKeyboard())
        compose.runOnIdle { searching = false }
        assertEquals(true, coversKeyboard())
    }

    @Test
    fun aSheetTakesTheKeyboardOnlyForAField() = followsItsField {
        InsetModalSheet({}, halfFirst = false) { Body() }
    }

    @Test
    fun aCardTakesTheKeyboardOnlyForAField() = followsItsField {
        CenteredCard({}) { Body() }
    }

    @Test
    fun aSheetThatOpensOnAFieldHasTheKeyboard() {
        searching = true
        show { InsetModalSheet({}, halfFirst = false) { Body() } }
        assertEquals(false, coversKeyboard())
    }

    @Composable
    private fun Body() {
        Text("Model")
        if (searching) SearchField("", {})
    }
}
