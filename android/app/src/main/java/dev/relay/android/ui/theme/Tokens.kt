package dev.relay.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The corners in docs/expressive/design-brief.md ("Shape and spacing"). */
@Immutable
object RelayShapes {
    /** Circle buttons, pills, chips, the composer when collapsed. */
    val circle = CircleShape
    /** Thumbnails, inline code, small tiles. */
    val small = RoundedCornerShape(8.dp)
    /** A row's highlight in the drawer and lists. */
    val row = RoundedCornerShape(12.dp)
    /** Text fields that aren't pills. */
    val field = RoundedCornerShape(16.dp)
    /** Cards, banners, code blocks, dialogs' inner pieces. */
    val card = RoundedCornerShape(20.dp)
    /** The toast. */
    val toast = RoundedCornerShape(22.dp)
    /** The composer once it has grown into a card. */
    val composer = RoundedCornerShape(26.dp)
    /** Bottom sheets and dialogs. */
    val sheet = RoundedCornerShape(28.dp)
    /** The content sheet's top corners over the chrome. */
    val content = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    /** A group's outer corners, and the small ones where its rows meet. */
    val groupOuter = 24.dp
    val groupInner = 6.dp

    /** A message bubble with several lines; one line is fully round. */
    val bubble = 22.dp
}

/** The 4dp grid; screens pad their sides by [lg]. */
@Immutable
object Space {
    val px1 = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/**
 * Springs per the brief's "Motion": [standard] is the theme's scheme and drives everything that
 * recurs (sheets, the drawer, pages, the composer, list items); [hero] is Material's expressive
 * scheme, for the few moments that should feel alive: the bar's pill sliding, the tooltip
 * popping, the send button appearing, a toast arriving.
 */
@Immutable
object RelayMotion {
    val standard: MotionScheme = MotionScheme.standard()
    val hero: MotionScheme = MotionScheme.expressive()
}

/** Tween curves for what isn't a spring: entrances timed against something else. */
object Motion {
    /** ease-out-quint: most things. */
    val EaseOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    /** The drawer's curve. */
    val Drawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    const val FAST = 150
    const val BASE = 200
    const val SLIDE = 300
}

/** One physical pixel. */
fun hairline(density: Float): Dp = (1f / density).dp
