package dev.relay.android.features.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.relay.android.features.updates.ApkInstall
import dev.relay.android.features.updates.AppOfferRow
import dev.relay.android.features.updates.InstalledAppRow
import dev.relay.android.features.updates.LookForAppRow
import dev.relay.android.features.updates.Updates
import dev.relay.android.features.updates.about
import dev.relay.android.features.updates.appOffer
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.ListGroup
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Download

/**
 * A newer app, offered from a corner where Settings can't be reached (the
 * pairing screen with nothing paired): a download icon with a dot, ringed by
 * a download's progress once one was asked for. Opens Settings' update rows.
 */
@Composable
internal fun PairUpdateButton(updates: Updates, modifier: Modifier = Modifier) {
    val apk by updates.installer.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    val offer = appOffer(updates) ?: return
    // A quiet prefetch isn't something the user started, so it shows no ring.
    val progress = (apk.about(offer.version) as? ApkInstall.Downloading)?.takeIf { !it.quiet }?.done
    val c = RelayTheme.colors
    Box(modifier) {
        CircleButton(
            TablerIcons.Outline.Download,
            "Update to Relay ${offer.version}",
            { open = true },
            Modifier.progressRing(progress, c.accent),
        )
        if (progress == null) Box(
            Modifier.align(Alignment.TopEnd).offset((-9).dp, 9.dp).size(11.dp)
                .border(2.dp, c.tool, CircleShape).padding(2.dp).background(c.accent, CircleShape),
        )
    }
    if (open) Sheet({ open = false }) {
        ListGroup {
            InstalledAppRow(updates)
            AppOfferRow(updates, offer)
            LookForAppRow(updates)
        }
    }
}

/** Traces the circle clockwise from its top as [progress] goes from 0 to 1. */
private fun Modifier.progressRing(progress: Float?, color: Color) = drawWithContent {
    drawContent()
    if (progress == null) return@drawWithContent
    val stroke = 2.dp.toPx()
    drawArc(
        color,
        startAngle = -90f,
        sweepAngle = 360f * progress.coerceIn(0f, 1f),
        useCenter = false,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(stroke),
    )
}
