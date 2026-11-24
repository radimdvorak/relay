package dev.relay.android.features.newthread

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.relay.android.features.composer.COMPOSER_BOTTOM
import dev.relay.android.lib.navigation.ComposerOpening
import dev.relay.android.lib.navigation.ComposerStart
import dev.relay.android.ui.theme.RelayTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A new thread's composer starts where Home's stood, and isn't left hanging there when no keyboard takes it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-mdpi")
class ComposerRiseTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(opening: ComposerOpening) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RelayTheme {
                Box(Modifier.fillMaxSize().windowInsetsPadding(rememberComposerRise(opening).floor)) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = COMPOSER_BOTTOM).size(50.dp).testTag("composer"))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    /** Between the composer and the window's end. */
    private fun under() = (compose.onRoot().getBoundsInRoot().bottom - compose.onNodeWithTag("composer").getBoundsInRoot().bottom).value

    @Test
    fun itStartsWhereHomesStood() {
        show(ComposerOpening(rise = 120))
        assertEquals(120f, under(), 1f)
    }

    @Test
    fun withoutAKeyboardItSettlesAtTheEnd() {
        show(ComposerOpening(rise = 120))
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(120f, under(), 1f)
        compose.mainClock.advanceTimeBy(1500)
        assertEquals(COMPOSER_BOTTOM.value, under(), 1f)
    }

    @Test
    fun forASheetItDoesntWaitForAKeyboard() {
        show(ComposerOpening(ComposerStart.Attach, rise = 120))
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(COMPOSER_BOTTOM.value, under(), 1f)
    }

    @Test
    fun fromNowhereItStartsAtTheEnd() {
        show(ComposerOpening())
        assertEquals(COMPOSER_BOTTOM.value, under(), 1f)
    }
}
