package dev.relay.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class CircleAction(val icon: ImageVector, val contentDescription: String, val onClick: () -> Unit)

/** Two halves of one pill: round on the outside, a small corner where they meet at the seam. */
object PairShapes {
    private val seam = CornerSize(6.dp)
    private val round = CornerSize(50)
    val start = RoundedCornerShape(round, seam, seam, round)
    val end = RoundedCornerShape(seam, round, round, seam)
}

/** Two header actions in one 88x44 pill with a seam between them (search | new thread). */
@Composable
fun CirclePair(
    first: CircleAction,
    second: CircleAction,
    modifier: Modifier = Modifier,
    tone: CircleTone = CircleTone.Raised,
) {
    val size = CircleSize.Header
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
        for ((action, shape) in listOf(first to PairShapes.start, second to PairShapes.end)) {
            CircleSurface(
                action.onClick, action.contentDescription, Modifier.size(size.diameter),
                shape, tone, enabled = true, haptic = null,
            ) { Icon(action.icon, null, size = size.icon) }
        }
    }
}
