package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayShapes
import dev.relay.android.ui.theme.RelayTheme

/** The rows of a [Group], added in order with [item]. */
class GroupScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

/**
 * Material 3 Expressive's tonal containment (Pixel Settings): related rows on one `container`
 * fill, each its own tile with 24dp outer corners at the group's ends and small ones where
 * neighbours meet, 2dp apart. In place of dividers.
 */
@Composable
fun Group(modifier: Modifier = Modifier, label: String? = null, content: GroupScope.() -> Unit) {
    val rows = GroupScope().apply(content).items
    Column(modifier.fillMaxWidth()) {
        if (label != null) SectionLabel(label, Modifier.padding(bottom = 6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { i, row ->
                val shape = groupTileShape(first = i == 0, last = i == rows.lastIndex)
                Box(Modifier.fillMaxWidth().clip(shape).background(RelayTheme.colors.container)) { row() }
            }
        }
    }
}

private fun groupTileShape(first: Boolean, last: Boolean): RoundedCornerShape {
    val outer = CornerSize(RelayShapes.groupOuter)
    val inner = CornerSize(RelayShapes.groupInner)
    val top = if (first) outer else inner
    val bottom = if (last) outer else inner
    return RoundedCornerShape(top, top, bottom, bottom)
}
