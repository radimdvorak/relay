package dev.relay.android.features.dictation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerRounding
import androidx.compose.foundation.shape.MorphPolygonShape
import androidx.compose.foundation.shape.PolygonShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.Icon
import dev.relay.android.ui.rememberReducedMotion
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Microphone
import kotlinx.coroutines.withTimeoutOrNull

/** Held longer than this, the mic is push-to-talk and letting go finishes. */
private const val HOLD_MS = 350L

/** How many of the latest levels the circle draws. */
internal const val MIC_BARS = 5

/** What the mic's circle is doing. */
internal data class MicLook(
    /** The composer's one dark circle, rather than a tool beside it. */
    val strong: Boolean,
    /** This composer is dictating, from the first tap until the words are in. */
    val live: Boolean = false,
    /** The engine isn't hearing yet. */
    val loading: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * The mic: a circle that turns into a soft flower while dictating, its petals and bars following
 * the voice. [levels] holds the last [MIC_BARS] loudnesses, 0 to 1. Tap to start and to finish,
 * or hold to talk: [onPress] says whether this press began a dictation, [onFinish] ends one.
 */
@Composable
internal fun MicCircle(
    look: MicLook,
    levels: () -> List<Float>,
    onPress: () -> Boolean,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = RelayTheme.colors
    val still = rememberReducedMotion()
    val live = animateFloatAsState(if (look.live) 1f else 0f, RelayTheme.motion.hero.defaultSpatialSpec(), label = "mic-live")
    val voice = animateFloatAsState(
        if (look.live && !still) levels().lastOrNull() ?: 0f else 0f,
        if (still) snap() else MaterialTheme.motionScheme.fastSpatialSpec(), label = "mic-voice",
    )
    val fill by animateColorAsState(if (look.strong) c.strong else c.tool, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "mic-fill")
    val content by animateColorAsState(if (look.strong) c.onStrong else c.ink, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "mic-icon")
    val shape = remember {
        MorphPolygonShape(
            PolygonShape.circle(PETALS),
            PolygonShape.star(PETALS, innerRadiusRatio = 0.8f, outerRounding = CornerRounding.fraction(0.5f), innerRounding = CornerRounding.fraction(0.5f)),
        ) { (live.value * (0.45f + 0.55f * voice.value)).coerceIn(0f, 1f) }
    }
    Box(
        modifier
            .size(CircleSize.Composer.diameter)
            .graphicsLayer {
                val grown = 1f + live.value * (0.04f + 0.12f * voice.value)
                scaleX = grown
                scaleY = grown
            }
            .alpha(if (look.enabled || look.live) 1f else 0.4f)
            .clip(shape)
            .background(fill)
            .micGestures(look, onPress, onFinish),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            TablerIcons.Outline.Microphone, null,
            Modifier.graphicsLayer {
                val gone = live.value.coerceIn(0f, 1f)
                alpha = 1f - gone
                scaleX = 1f - 0.5f * gone
                scaleY = 1f - 0.5f * gone
            },
            tint = content, size = CircleSize.Composer.icon,
        )
        VoiceBars(levels, if (look.loading) content.copy(alpha = 0.5f) else content, still, Modifier.graphicsLayer { alpha = live.value.coerceIn(0f, 1f) })
    }
}

/** The last few levels as bars, newest in the middle, so it reads as a voice rather than a scroll. */
@Composable
private fun VoiceBars(levels: () -> List<Float>, color: Color, still: Boolean, modifier: Modifier) {
    val now = levels()
    val heights = (0 until MIC_BARS).map { i ->
        val fromMiddle = kotlin.math.abs(i - MIC_BARS / 2)
        val level = now.getOrElse(now.lastIndex - fromMiddle) { 0f }
        animateFloatAsState(
            0.2f + 0.8f * level * (1f - 0.15f * fromMiddle),
            if (still) snap() else MaterialTheme.motionScheme.fastSpatialSpec(), label = "bar",
        )
    }
    Canvas(modifier.size(18.dp)) {
        val bar = 2.dp.toPx()
        val gap = (size.width - MIC_BARS * bar) / (MIC_BARS - 1)
        heights.forEachIndexed { i, height ->
            val tall = size.height * height.value.coerceIn(0.1f, 1f)
            drawRoundRect(color, Offset(i * (bar + gap), (size.height - tall) / 2), Size(bar, tall), CornerRadius(bar / 2))
        }
    }
}

@Composable
private fun Modifier.micGestures(look: MicLook, onPress: () -> Boolean, onFinish: () -> Unit): Modifier {
    val usable by rememberUpdatedState(look.enabled || look.live)
    val press by rememberUpdatedState(onPress)
    val finish by rememberUpdatedState(onFinish)
    return this
        .semantics {
            role = Role.Button
            contentDescription = if (look.live) "Finish dictation" else "Dictate"
            onClick {
                if (!usable) return@onClick false
                if (!press()) finish()
                true
            }
        }
        // Never restarted while a press is down, or letting go of a hold would go unheard.
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                if (!usable) return@detectTapGestures
                val began = press()
                val tapped = withTimeoutOrNull(HOLD_MS) { tryAwaitRelease() } != null
                if (!tapped) tryAwaitRelease()
                // A tap on the live circle finishes; so does letting go after holding.
                if (!began || !tapped) finish()
            })
        }
}

private const val PETALS = 8
