package dev.relay.android.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Check

/** A row of a pick-one list: lit, with a check, when it's the chosen one. */
@Composable
internal fun ChoiceRow(label: String, hint: String?, chosen: Boolean, onClick: () -> Unit) {
    ListRow(
        label,
        subtitle = hint,
        subtitleLines = 3,
        selected = chosen,
        trailing = if (chosen) ({ Icon(TablerIcons.Outline.Check, null, tint = RelayTheme.colors.ink, size = 20.dp) }) else null,
        onClick = onClick,
    )
}
