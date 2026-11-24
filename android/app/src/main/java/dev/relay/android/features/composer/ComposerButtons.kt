package dev.relay.android.features.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.Haptic
import dev.relay.android.ui.Icon
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.filled.PlayerStop
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowUp
import io.github.lyxnx.compose.ui.tablericons.outline.Bolt
import io.github.lyxnx.compose.ui.tablericons.outline.Edit
import io.github.lyxnx.compose.ui.tablericons.outline.PlaylistAdd
import io.github.lyxnx.compose.ui.tablericons.outline.X
import kotlin.math.roundToInt

/** What the composer's buttons go by. */
internal data class SendState(
    val empty: Boolean,
    val running: Boolean = false,
    /** A turn runs and there's nothing to say: the button stops it. */
    val stop: Boolean = false,
    val steering: Boolean = false,
    val retryingScheduled: Boolean = false,
    val enabled: Boolean = true,
    /** Words are coming in from this composer's mic. */
    val dictating: Boolean = false,
    val discardable: Boolean = false,
)

private enum class SendRole(val icon: ImageVector, val iconSize: Dp) {
    Send(TablerIcons.Outline.ArrowUp, 20.dp),
    Queue(TablerIcons.Outline.PlaylistAdd, 18.dp),
    Stop(TablerIcons.Filled.PlayerStop, 16.dp),
    NewThread(TablerIcons.Outline.Edit, 18.dp),
}

private fun SendState.role(newThread: Boolean) = when {
    newThread -> SendRole.NewThread
    stop -> SendRole.Stop
    running -> SendRole.Queue
    else -> SendRole.Send
}

private fun SendState.label() = when {
    stop -> "Stop"
    retryingScheduled -> "Retry scheduled send"
    running -> "Queue"
    else -> "Send"
}

/**
 * The composer's end: one strong circle that changes role. Nothing typed, it's the mic; with
 * something to say it's Send (Queue while a turn runs, Steer beside it); with a turn running and
 * nothing typed it's Stop; while dictating it's the mic again, and Send waits next to it in the
 * tool fill. [mic] draws the mic, told whether it's the strong one; null where there's none.
 * The mic never leaves its place in composition, so a press that started it can end it.
 */
@Composable
internal fun SendButtons(
    state: SendState,
    onSteer: () -> Unit,
    onSend: () -> Unit,
    onLater: () -> Unit,
    onDiscardDictation: () -> Unit,
    mic: (@Composable (strong: Boolean) -> Unit)?,
    /** Draws Send as the New thread button, with this modifier on it. */
    newThread: Modifier? = null,
) {
    val shown = mic == null || newThread != null || state.stop || !state.empty
    Row(verticalAlignment = Alignment.CenterVertically) {
        Appearing(state.discardable) {
            CircleButton(
                TablerIcons.Outline.X, "Discard dictation", onDiscardDictation,
                Modifier.padding(end = 8.dp), size = CircleSize.Composer,
            )
        }
        Appearing(state.steering) {
            CircleButton(
                TablerIcons.Outline.Bolt, "Steer now", onSteer,
                Modifier.padding(end = 8.dp), size = CircleSize.Composer, enabled = state.enabled, haptic = Haptic.Medium,
            )
        }
        mic?.invoke(!shown || state.dictating)
        Appearing(shown) { SendCircle(state, onSend, onLater, newThread, Modifier.padding(start = if (mic != null) 8.dp else 0.dp)) }
    }
}

@Composable
private fun SendCircle(state: SendState, onSend: () -> Unit, onLater: () -> Unit, newThread: Modifier?, modifier: Modifier) {
    val c = RelayTheme.colors
    val role = state.role(newThread != null)
    val ready = state.stop || !state.empty || newThread != null
    val strong = ready && !state.dictating
    val fills = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val fill by animateColorAsState(
        if (!strong) c.tool else if (role == SendRole.Stop) c.strong else c.send, fills, label = "send-fill",
    )
    val tint by animateColorAsState(
        if (!ready) c.inkFaint else if (!strong) c.ink else if (role == SendRole.Stop) c.onStrong else c.onSend, fills, label = "send-icon",
    )
    Box(
        modifier
            .then(newThread ?: Modifier)
            .pressable(
                onClick = onSend,
                enabled = state.enabled && (state.stop || !state.empty),
                onLongClick = if (state.empty) null else onLater,
            )
            .size(CircleSize.Composer.diameter)
            .alpha(if (state.enabled) 1f else 0.4f)
            .clip(RelayTheme.shapes.circle)
            .background(fill)
            .semantics { contentDescription = state.label() },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(role, transitionSpec = {
            fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.6f) togetherWith fadeOut(tween(80))
        }, contentAlignment = Alignment.Center, label = "send-role") { Icon(it.icon, null, tint = tint, size = it.iconSize) }
    }
}

/** Makes room as it comes, then pops into it; gone, it takes none. */
@Composable
private fun Appearing(visible: Boolean, content: @Composable () -> Unit) {
    val target = if (visible) 1f else 0f
    val room = animateFloatAsState(target, MaterialTheme.motionScheme.fastSpatialSpec(), label = "room")
    val pop = animateFloatAsState(target, RelayTheme.motion.hero.fastSpatialSpec(), label = "pop")
    val alpha = animateFloatAsState(target, MaterialTheme.motionScheme.fastEffectsSpec(), label = "alpha")
    val gone by remember { derivedStateOf { room.value < 0.01f } }
    if (!visible && gone) return
    Box(
        Modifier
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                val width = (p.width * room.value.coerceIn(0f, 1f)).roundToInt()
                layout(width, p.height) { p.place((width - p.width) / 2, 0) }
            }
            .graphicsLayer {
                scaleX = pop.value.coerceAtLeast(0f)
                scaleY = pop.value.coerceAtLeast(0f)
                this.alpha = alpha.value
            },
    ) { content() }
}
