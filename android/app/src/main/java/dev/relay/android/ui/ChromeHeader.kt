package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme

/** Centred: a thread's or a page's name between the buttons. Large: a tab's title, left-aligned. */
enum class HeaderTitle { Centered, Large }

/**
 * The chrome band over a [ContentSheet]: a 44dp circle at the start (☰ or ←), a title, and
 * circles or a [CirclePair] at the end. Pads the status bar itself.
 */
@Composable
fun ChromeHeader(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleStyle: HeaderTitle = HeaderTitle.Centered,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(RelayTheme.colors.chrome)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(44.dp),
    ) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            if (title != null && titleStyle == HeaderTitle.Large) Text(
                title,
                Modifier.padding(start = if (leading != null) 12.dp else 4.dp),
                style = RelayTheme.type.title,
                maxLines = 1,
            )
        }
        if (title != null && titleStyle == HeaderTitle.Centered) Text(
            title,
            // Clear of a 44dp circle on the left and a pair on the right, so a long name never runs under them.
            Modifier.align(Alignment.Center).padding(horizontal = 100.dp),
            style = RelayTheme.type.body.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}
