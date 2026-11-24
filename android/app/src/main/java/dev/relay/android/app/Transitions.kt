package dev.relay.android.app

import androidx.compose.animation.ContentTransform
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

/** Whether it takes the last screen's place: search, and new thread, whose composer grows out of Home's by itself. */
private val Scene<*>.fades get() = has(FADES_IN) || has(MORPHS_IN)

/** How [target] comes in: over the last screen, or in its place when the drawer or the side pane [swapped] them. */
internal fun forward(target: Scene<*>, swapped: Boolean): ContentTransform =
    if (swapped || target.fades) crossfade() else slideIn()

/** How [leaving] goes back where it came from. */
internal fun back(leaving: Scene<*>): ContentTransform = if (leaving.fades) crossfade() else slideOut()

private fun crossfade() = fadeIn(effects) togetherWith fadeOut(effects)

private fun slideIn() = slideInHorizontally(move) { it } togetherWith slideOutHorizontally(move) { -it / 4 }
private fun slideOut() = slideInHorizontally(move) { -it / 4 } togetherWith slideOutHorizontally(move) { it }
