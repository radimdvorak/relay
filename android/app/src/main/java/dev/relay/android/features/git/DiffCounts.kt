package dev.relay.android.features.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme

/** A row's quiet second line: 13sp. */
internal val rowMeta: TextStyle @Composable get() = RelayTheme.type.meta.copy(fontSize = 13.sp)

/** "+46 −31" in the diff's colours; a side with nothing is left out. */
@Composable
fun DiffCounts(additions: Int, deletions: Int, modifier: Modifier = Modifier) {
    val c = RelayTheme.colors
    val style = rowMeta.copy(fontFeatureSettings = "tnum")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (additions > 0) Text("+$additions", style = style, color = c.added, maxLines = 1)
        if (deletions > 0) Text("−$deletions", style = style, color = c.removed, maxLines = 1)
    }
}
