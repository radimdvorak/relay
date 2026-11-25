package dev.relay.android.features.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Icon
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowDown

private val FloatSize = 40.dp

/**
 * What floats over the thread's last lines: the way back down in the middle, the plan's rings at
 * the end. Its height is the same whichever of them show, so they come and go without a layout.
 */
@Composable
internal fun ThreadFloating(scroll: ThreadScroll, rings: (@Composable () -> Unit)?) {
    Box(Modifier.fillMaxWidth().padding(start = DockInset, end = DockInset, bottom = 10.dp)) {
        JumpToLatest(scroll)
        // Its own backing, or the rings read as a spinner on whatever scrolls under them.
        if (rings != null) Box(
            Modifier.align(Alignment.CenterEnd).size(FloatSize).floats(),
            contentAlignment = Alignment.Center,
        ) { rings() }
    }
}

/** Back to the newest message, while the reader is further up than that. */
@Composable
private fun BoxScope.JumpToLatest(scroll: ThreadScroll) {
    val away by remember(scroll) { derivedStateOf { scroll.reading && scroll.list.canScrollForward } }
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val grow = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    Box(Modifier.align(Alignment.Center).size(FloatSize)) {
        AnimatedVisibility(
            away,
            enter = fadeIn(fade) + scaleIn(grow, initialScale = 0.8f),
            exit = fadeOut(fade) + scaleOut(grow, targetScale = 0.8f),
        ) {
            Box(
                Modifier.size(FloatSize).pressable(onClick = { scroll.toBottom("jump_to_latest") })
                    .semantics { contentDescription = "Jump to latest" }.floats(),
                contentAlignment = Alignment.Center,
            ) { Icon(TablerIcons.Outline.ArrowDown, null, size = 20.dp, tint = RelayTheme.colors.ink) }
        }
    }
}

@Composable
private fun Modifier.floats(): Modifier {
    val shape = RelayTheme.shapes.circle
    val shade = Color.Black.copy(alpha = 0.12f)
    return shadow(8.dp, shape, ambientColor = shade, spotColor = shade).background(RelayTheme.colors.container, shape)
}
