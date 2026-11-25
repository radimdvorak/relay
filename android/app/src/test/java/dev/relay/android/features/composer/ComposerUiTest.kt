package dev.relay.android.features.composer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.relay.android.features.dictation.MicCircle
import dev.relay.android.features.dictation.MicLook
import dev.relay.android.features.composer.logic.effortLevels
import dev.relay.android.ui.theme.RelayPaperLight
import dev.relay.android.ui.theme.RelayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What must hold however the composer looks: the field's state, what the wheel and the slider pick, the mic's hold. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class ComposerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        RelayTheme(RelayPaperLight) { Column(Modifier.fillMaxSize()) { content() } }
    }

    @Test fun `growing and shrinking keeps the focus, the text and the selection`() {
        var expanded by mutableStateOf(false)
        val field = TextFieldState("")
        show {
            ComposerBox(
                expanded, ModelChoices("claude", "Opus 5.5", "High"), {}, {}, {}, {},
                field = { ComposerField(field, "Message", remember { FocusRequester() }, { expanded = it }, {}, {}) },
                buttons = { SendButtons(SendState(empty = field.text.isEmpty()), {}, {}, {}, {}, mic = null) },
            )
        }
        val message = compose.onNodeWithContentDescription("Message")
        message.performClick()
        compose.waitForIdle()
        assertEquals(true, expanded)
        message.assertIsFocused()
        message.performTextInput("half a thought")
        compose.runOnIdle { field.edit { selection = TextRange(5, 9) } }
        for (now in listOf(false, true)) {
            compose.runOnIdle { expanded = now }
            compose.waitForIdle()
            message.assertIsFocused()
            assertEquals("half a thought", field.text.toString())
            assertEquals(TextRange(5, 9), field.selection)
        }
    }

    private fun wheel(names: () -> List<WheelRow>, picks: MutableList<String>, start: String) = show {
        var chosen by remember { mutableStateOf(start) }
        ModelWheel(names(), chosen, {
            picks += it
            chosen = it
        }, Modifier.testTag("wheel"))
    }

    private val sixty = (1..60).map { WheelRow("m$it", "Model $it") }

    @Test fun `opening the wheel, and its list arriving late, pick nothing`() {
        val picks = mutableListOf<String>()
        var rows by mutableStateOf(listOf(WheelRow("", "Default"), WheelRow("m30", "m30")))
        wheel({ rows }, picks, "m30")
        compose.waitForIdle()
        compose.runOnIdle { rows = listOf(WheelRow("", "Default")) + sixty }
        compose.waitForIdle()
        compose.onNodeWithText("Model 30").assertExists()
        assertEquals(emptyList<String>(), picks)
    }

    @Test fun `one model has nothing to roll to`() {
        val picks = mutableListOf<String>()
        wheel({ listOf(WheelRow("", "Default")) }, picks, "")
        compose.onNodeWithTag("wheel").performTouchInput { swipeUp() }
        compose.onNodeWithText("Default").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<String>(), picks)
    }

    @Test fun `a tap rolls to that row and picks it once`() {
        val picks = mutableListOf<String>()
        wheel({ sixty }, picks, "m30")
        compose.onNodeWithText("Model 31").performClick()
        compose.waitForIdle()
        assertEquals(listOf("m31"), picks)
    }

    @Test fun `a swipe picks the row it comes to rest on, once`() {
        val picks = mutableListOf<String>()
        wheel({ sixty }, picks, "m30")
        compose.onNodeWithTag("wheel").performTouchInput { swipeUp(startY = centerY, endY = centerY - 57f * density * 2, durationMillis = 1500) }
        compose.waitForIdle()
        assertEquals(1, picks.size)
        assertEquals(true, picks.single().drop(1).toInt() in 31..34)
        compose.onNodeWithText(sixty.first { it.id == picks.single() }.name).assertExists()
    }

    @Test fun `the slider moves by keyboard and TalkBack, one stop at a time`() {
        var effort by mutableStateOf("")
        val levels = effortLevels(listOf("low", "medium", "high"), "medium")
        show { EffortPage(levels, effort, { effort = it }) }
        val slider = compose.onNodeWithContentDescription("Effort")
        slider.assert(hasStateDescription("Medium"))
        slider.requestFocus()
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        assertEquals("high", effort)
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals("high", effort)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.waitForIdle()
        assertEquals("low", effort)
        slider.assert(hasStateDescription("Low"))
        compose.onNodeWithContentDescription("Back to the default, Medium").performClick()
        assertEquals("", effort)
        slider.assert(hasStateDescription("Medium"))
    }

    @Test fun `holding the mic talks until it's let go, a tap leaves it on`() {
        var live by mutableStateOf(false)
        var finished = 0
        show {
            MicCircle(MicLook(strong = true, live = live), { emptyList() }, onPress = {
                val began = !live
                live = true
                began
            }, onFinish = {
                finished++
                live = false
            })
        }
        // Found by place, as its label changes under the press.
        val mic = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        mic.performTouchInput { down(center); up() }
        compose.waitForIdle()
        assertEquals(true, live)
        assertEquals(0, finished)
        mic.performTouchInput { down(center); up() }
        compose.waitForIdle()
        assertEquals(1, finished)

        mic.performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(true, live)
        compose.mainClock.advanceTimeBy(400)
        mic.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(2, finished)
    }
}
