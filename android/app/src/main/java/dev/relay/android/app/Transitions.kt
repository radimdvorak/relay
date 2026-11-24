package dev.relay.android.app

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.scene.Scene
import dev.relay.android.lib.navigation.FADES_IN
import dev.relay.android.lib.navigation.MORPHS_IN
import dev.relay.android.ui.theme.RelayMotion

// Pages move on the standard springs: quick, decelerating, nothing past its end. Alpha has its own.
private val move = RelayMotion.standard.defaultSpatialSpec<IntOffset>()
private val scale = RelayMotion.standard.defaultSpatialSpec<Float>()
private val effects = RelayMotion.standard.defaultEffectsSpec<Float>()
private val quick = RelayMotion.standard.fastEffectsSpec<Float>()

private fun Scene<*>.has(mark: String) = entries.lastOrNull()?.metadata?.get(mark) == true

/** How [target] comes in: over the last screen, or in its place when the drawer or the side pane [swapped] them. */
internal fun forward(target: Scene<*>, swapped: Boolean, wide: Boolean): ContentTransform = when {
    swapped || target.has(FADES_IN) -> crossfade()
    !wide && target.has(MORPHS_IN) -> riseIn()
    else -> slideIn()
}

/** How [leaving] goes back where it came from. */
internal fun back(leaving: Scene<*>, wide: Boolean): ContentTransform = when {
    leaving.has(FADES_IN) -> crossfade()
    !wide && leaving.has(MORPHS_IN) -> sinkOut()
    else -> slideOut()
}

private fun crossfade() = fadeIn(effects) togetherWith fadeOut(effects)

private fun slideIn() = slideInHorizontally(move) { it } togetherWith slideOutHorizontally(move) { -it / 4 }
private fun slideOut() = slideInHorizontally(move) { -it / 4 } togetherWith slideOutHorizontally(move) { it }

// The screen rises into place behind its button, which flies into it on its own (shared
// bounds), and the one under it sinks back a little, as if the new one came up off the glass.
private fun riseIn() = (slideInVertically(move) { it / 10 } + fadeIn(effects)) togetherWith
    (scaleOut(scale, targetScale = 0.96f) + fadeOut(effects))
private fun sinkOut() = (scaleIn(scale, initialScale = 0.96f) + fadeIn(effects)) togetherWith
    (slideOutVertically(move) { it / 10 } + fadeOut(quick))
