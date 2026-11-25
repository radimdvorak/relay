package dev.relay.android.features.newthread

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.relay.android.features.composer.COMPOSER_BOTTOM
import dev.relay.android.lib.navigation.ComposerOpening
import dev.relay.android.lib.navigation.ComposerStart
import dev.relay.android.ui.LocalScreenAnimation
import dev.relay.android.ui.screenSettled
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.math.roundToInt

/** A keyboard that takes longer isn't coming: a hardware one is attached. */
private const val KEYBOARD_WAIT_MS = 1200L

/** The intro's height over the composer and its keyboard, as last seen, for a window of [window]'s height. */
private object IntroOverKeyboard {
    var window = 0
    var height = 0
}

/**
 * A new thread's composer on its way up from Home's: it starts where that one stands and leaves
 * with the keyboard, not once that has reached it at full speed. The intro is laid out for the
 * keyboard from the start, so the composer is all that moves.
 */
internal class ComposerRise(
    val inPlace: Boolean,
    awaitsKeyboard: Boolean,
    private val from: Int,
    private val window: Int,
    private val ime: WindowInsets,
    private val imeTarget: WindowInsets,
) {
    /** How much of [from] still counts. */
    val left = Animatable(if (inPlace) 1f else 0f)

    /** The keyboard isn't coming yet; the composer grows as it does. */
    var waiting by mutableStateOf(inPlace && awaitsKeyboard)

    var landed by mutableStateOf(!inPlace)

    val introAlpha = Animatable(if (inPlace) 0f else 1f)

    /** What the composer stands on: from Home's place to the keyboard's, as far along as the keyboard is. */
    val floor = object : WindowInsets {
        override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
        override fun getTop(density: Density) = 0
        override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
        override fun getBottom(density: Density): Int {
            val start = from * left.value
            val end = imeTarget.getBottom(density)
            if (end <= start) return start.roundToInt()
            return (start + (end - start) * ime.getBottom(density) / end).roundToInt()
        }
    }

    private var introRoom = 0

    val introInPlace = Modifier.graphicsLayer { alpha = introAlpha.value }.layout { measurable, constraints ->
        val room = constraints.maxHeight
        introRoom = room
        val known = IntroOverKeyboard.height.takeIf { IntroOverKeyboard.window == window } ?: room
        val height = (room + (min(known, room) - room) * left.value).roundToInt()
        val intro = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        layout(intro.width, room) { intro.place(0, 0) }
    }

    fun rememberIntroOverKeyboard() {
        IntroOverKeyboard.window = window
        IntroOverKeyboard.height = introRoom
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberComposerRise(opening: ComposerOpening): ComposerRise {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize.height
    val ime = WindowInsets.ime
    val imeTarget = WindowInsets.imeAnimationTarget
    val rise = remember {
        val from = opening.rise?.let { it - with(density) { COMPOSER_BOTTOM.roundToPx() } }
        ComposerRise(inPlace = from != null, awaitsKeyboard = opening.start == ComposerStart.Type, from = from ?: 0, window, ime, imeTarget)
    }
    IntroFade(rise)
    val keyboardComes = { imeTarget.getBottom(density) > 0 }
    val keyboardIsUp = { ime.getBottom(density) > 0 && ime.getBottom(density) == imeTarget.getBottom(density) }
    val settled by rememberUpdatedState(screenSettled())
    val settle = RelayTheme.motion.standard.defaultSpatialSpec<Float>()
    LaunchedEffect(rise) {
        if (!rise.inPlace) return@LaunchedEffect
        if (rise.waiting) withTimeoutOrNull(KEYBOARD_WAIT_MS) {
            snapshotFlow(keyboardComes).first { it }
            rise.waiting = false
            snapshotFlow(keyboardIsUp).first { it }
        } else snapshotFlow { settled }.first { it }
        rise.waiting = false
        rise.left.animateTo(0f, settle)
        if (keyboardIsUp()) rise.rememberIntroOverKeyboard()
        rise.landed = true
    }
    return rise
}

/** The intro comes once the list it replaces is covered, and goes before that shows again. */
@Composable
private fun IntroFade(rise: ComposerRise) {
    val leaving = LocalScreenAnimation.current?.transition?.targetState == EnterExitState.PostExit
    LaunchedEffect(rise, leaving) {
        if (!rise.inPlace) return@LaunchedEffect
        if (leaving) {
            rise.introAlpha.animateTo(0f, tween(90))
            return@LaunchedEffect
        }
        // The frame that builds the screen is a long one; what starts in it is half over when it shows.
        repeat(2) { withFrameNanos { } }
        rise.introAlpha.animateTo(1f, tween(200, delayMillis = 120))
    }
}
