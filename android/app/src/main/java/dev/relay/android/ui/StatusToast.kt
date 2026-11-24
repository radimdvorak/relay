package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme

/**
 * A thread's news, floating right-aligned above the composer: a small line with the thread's
 * icon and title, and the status under it in larger ink ("Finished", "Needs you"). Its look
 * only; the shell decides when it comes and goes.
 */
@Composable
fun StatusToast(
    thread: String,
    status: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    statusColor: Color = RelayTheme.colors.ink,
) {
    val c = RelayTheme.colors
    val shape = RelayTheme.shapes.toast
    Column(
        modifier
            .widthIn(min = 160.dp, max = 280.dp)
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .background(c.container, shape)
            .ring(c.line, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, null, tint = c.inkSoft, size = 14.dp)
            Text(thread, style = RelayTheme.type.small, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(status, style = RelayTheme.type.row, color = statusColor, maxLines = 1)
    }
}
