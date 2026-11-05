package dev.relay.android.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.DEFAULT_CONTRAST
import dev.relay.android.ui.theme.RelayTheme
import dev.relay.android.ui.theme.ThemeSettings
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Contrast
import kotlin.math.roundToInt

/** The phone's own contrast over whichever theme it wears; the whole app recolours as it moves. */
@Composable
internal fun ContrastRow(theme: ThemeSettings) {
    val c = RelayTheme.colors
    Column {
        ListRow(
            "Contrast",
            subtitle = "On top of the theme, on this phone only.",
            leadingIcon = TablerIcons.Outline.Contrast,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (theme.contrast != DEFAULT_CONTRAST) Text(
                        "Reset",
                        Modifier.pressable({ theme.contrast = DEFAULT_CONTRAST }),
                        style = RelayTheme.type.xs,
                        color = c.mutedForeground,
                    )
                    Text("${theme.contrast}", style = RelayTheme.type.xs, color = c.foreground)
                }
            },
        )
        Slider(
            value = theme.contrast.toFloat(),
            onValueChange = { theme.contrast = it.roundToInt() },
            valueRange = 0f..100f,
            modifier = Modifier.padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = c.foreground,
                activeTrackColor = c.foreground,
                inactiveTrackColor = c.muted,
            ),
        )
    }
}
