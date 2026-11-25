package dev.relay.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

data class CircleAction(val icon: ImageVector, val contentDescription: String, val onClick: () -> Unit)

/** Two halves of one pill: round on the outside, a small corner where they meet at the seam. */
object PairShapes {
    private val seam = CornerSize(6.dp)
    private val round = CornerSize(50)
    val start = RoundedCornerShape(round, seam, seam, round)
    val end = RoundedCornerShape(seam, round, round, seam)

    /** The first half on its way to being a field: the corners at the seam round off as it widens. */
    val startGrowing = object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val full = size.height / 2
            val seamAtRest = seam.toPx(size, density)
            val widened = ((size.width - size.height) / size.height).coerceIn(0f, 1f)
            val corner = CornerSize(seamAtRest + (full - seamAtRest) * widened)
            return RoundedCornerShape(round, corner, corner, round).createOutline(size, layoutDirection, density)
        }
    }
}

/** Two header actions in one 88x44 pill with a seam between them (search | new thread). */
@Composable
fun CirclePair(
    first: CircleAction,
    second: CircleAction,
    modifier: Modifier = Modifier,
    tone: CircleTone = CircleTone.Raised,
    /** For a first half that travels to the next screen. */
    firstModifier: Modifier = Modifier,
) {
    val size = CircleSize.Header
    val iconInset = (size.diameter - size.icon) / 2
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
        CircleSurface(
            first.onClick, first.contentDescription, firstModifier.size(size.diameter),
            PairShapes.start, tone, enabled = true, haptic = null,
        ) {
            // Held at the start, where it stays when this half widens.
            Box(Modifier.fillMaxSize().padding(start = iconInset), contentAlignment = Alignment.CenterStart) {
                Icon(first.icon, null, size = size.icon)
            }
        }
        CircleSurface(
            second.onClick, second.contentDescription, Modifier.size(size.diameter),
            PairShapes.end, tone, enabled = true, haptic = null,
        ) { Icon(second.icon, null, size = size.icon) }
    }
}
