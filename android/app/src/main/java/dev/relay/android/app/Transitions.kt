package dev.relay.android.app

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.scene.Scene
import dev.relay.android.lib.navigation.FADES_IN
import dev.relay.android.lib.navigation.MORPHS_IN
import dev.relay.android.ui.theme.RelayMotion

// Pages move on the standard springs: quick, decelerating, nothing past its end. Alpha has its own.
private val move = RelayMotion.standard.defaultSpatialSpec<IntOffset>()
private val effects = RelayMotion.standard.defaultEffectsSpec<Float>()

private fun Scene<*>.has(mark: String) = entries.lastOrNull()?.metadata?.get(mark) == true

/**
 * New thread's composer stands where Home's does, so only what's around it changes. Slow to start:
 * the frame that builds the screen takes a good part of this, and a linear fade would be over by the
 * first one seen.
 */
private const val COVER_MS = 220
private val covers = tween<Float>(COVER_MS, easing = FastOutLinearInEasing)

/** How [target] comes in: over the last screen, or in its place when the list beside it [swapped] them. */
internal fun forward(target: Scene<*>, swapped: Boolean): ContentTransform =
    when {
        swapped || target.has(FADES_IN) -> crossfade()
        target.has(MORPHS_IN) -> cover()
        else -> slideIn()
    }

/** How [leaving] goes back where it came from. */
internal fun back(leaving: Scene<*>): ContentTransform =
    when {
        leaving.has(FADES_IN) -> crossfade()
        leaving.has(MORPHS_IN) -> uncover()
        else -> slideOut()
    }

private fun crossfade() = fadeIn(effects) togetherWith fadeOut(effects)

// Over a whole screen rather than against its fading: two composers at half strength would dip
// together. The screen's own content comes after this and is gone before it, so no text shows through text.
private fun cover() = fadeIn(covers) togetherWith fadeOut(snap(COVER_MS))
private fun uncover() = EnterTransition.None togetherWith fadeOut(covers)

private fun slideIn() = slideInHorizontally(move) { it } togetherWith slideOutHorizontally(move) { -it / 4 }
private fun slideOut() = slideInHorizontally(move) { -it / 4 } togetherWith slideOutHorizontally(move) { it }
