package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.ui.theme.RelayTheme

/** The label that pops over a bar icon: white on strong, fully round. */
@Composable
fun BarTooltip(text: String, modifier: Modifier = Modifier) {
    val c = RelayTheme.colors
    Text(
        text,
        modifier
            .height(30.dp)
            .background(c.strong, RelayTheme.shapes.circle)
            .padding(horizontal = 12.dp)
            .wrapContentHeight(),
        style = RelayTheme.type.label.copy(fontSize = 14.sp, lineHeight = 18.sp),
        color = c.onStrong,
        maxLines = 1,
    )
}
