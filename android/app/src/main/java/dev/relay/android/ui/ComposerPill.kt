package dev.relay.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.relay.android.ui.theme.RelayTheme
import kotlin.math.roundToInt

/** The collapsed pill's height. */
val ComposerPillHeight = 53.dp

private val Inset = 8.5.dp
private val Gap = 8.dp
private val FieldTop = 14.dp
private val FieldSide = 16.dp
private val FieldToRow = 10.dp

/**
 * The composer's look, without its behaviour. Collapsed: one 53dp pill, [leading] · [field] ·
 * [trailing]. Expanded: a card with the field on top and a row under it: [leading], [chips],
 * then [trailing] at the end. The slots keep their place in composition while it moves between
 * the two, so a focused field stays focused. [header] (attachments) sits over the field and is
 * only drawn expanded.
 */
@Composable
fun ComposerPill(
    field: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    leading: @Composable RowScope.() -> Unit = {},
    chips: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    header: @Composable () -> Unit = {},
) {
    val c = RelayTheme.colors
    val shape = RelayTheme.shapes.composer
    val progress = animateFloatAsState(
        if (expanded) 1f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "composer-expand",
    )
    val row = { slot: @Composable RowScope.() -> Unit ->
        @Composable {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Gap),
                verticalAlignment = Alignment.CenterVertically,
                content = slot,
            )
        }
    }
    Layout(
        contents = listOf(row(leading), field, row(chips), row(trailing), { Column { header() } }),
        modifier = modifier
            // The system already draws shadows faint; a colour thinner than this one vanishes on the surface.
            .shadow(8.dp, shape, ambientColor = Color.Black, spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(shape)
            .background(c.raised),
    ) { (leadSlot, fieldSlot, chipSlot, trailSlot, headerSlot), constraints ->
        val t = progress.value
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val lead = leadSlot.first().measure(loose)
        val trail = trailSlot.first().measure(loose)
        val inset = Inset.roundToPx()
        val gap = Gap.roundToPx()

        fun besideGap(p: Placeable) = if (p.width > 0) p.width + gap else 0
        val chipsWidth = (width - 2 * inset - besideGap(lead) - besideGap(trail)).coerceAtLeast(0)
        val chipRow = chipSlot.first().measure(Constraints(maxWidth = chipsWidth))
        val over = headerSlot.first().measure(loose)
        val collapsedFieldX = inset + besideGap(lead).coerceAtLeast(FieldSide.roundToPx() - inset)
        val collapsedFieldWidth = width - collapsedFieldX - inset - besideGap(trail)
        val expandedFieldX = FieldSide.roundToPx()
        val expandedFieldWidth = width - 2 * expandedFieldX
        val fieldX = lerp(collapsedFieldX, expandedFieldX, t)
        val fieldWidth = lerp(collapsedFieldWidth, expandedFieldWidth, t).coerceAtLeast(0)
        val text = fieldSlot.first().measure(Constraints(maxWidth = fieldWidth))

        val controls = maxOf(lead.height, trail.height, chipRow.height)
        val collapsedHeight = maxOf(ComposerPillHeight.roundToPx(), text.height + 2 * inset, controls + 2 * inset)
        val fieldTop = over.height + FieldTop.roundToPx()
        val controlsTop = fieldTop + text.height + FieldToRow.roundToPx()
        val expandedHeight = controlsTop + controls + inset
        val height = lerp(collapsedHeight, expandedHeight, t)

        // Collapsed, everything is centred on the pill; expanded, the controls share a row under the field.
        fun centredIn(h: Int, p: Placeable) = (h - p.height) / 2
        fun onControlRow(p: Placeable) = controlsTop + (controls - p.height) / 2
        fun y(p: Placeable) = lerp(centredIn(collapsedHeight, p), onControlRow(p), t)
        layout(width, height) {
            lead.place(inset, y(lead))
            text.place(fieldX, lerp(centredIn(collapsedHeight, text), fieldTop, t))
            // Left out while collapsed, so invisible chips and attachments can't take taps.
            if (t > 0f) {
                over.placeWithLayer(0, 0) { alpha = t.coerceIn(0f, 1f) }
                chipRow.placeWithLayer(inset + besideGap(lead), y(chipRow)) { alpha = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f) }
            }
            trail.place(width - inset - trail.width, y(trail))
        }
    }
}

/** The hint in an empty composer field. */
@Composable
fun ComposerPlaceholder(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = RelayTheme.type.row, color = RelayTheme.colors.inkFaint, maxLines = 1)
}
