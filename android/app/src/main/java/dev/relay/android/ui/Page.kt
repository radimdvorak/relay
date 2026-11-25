package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft

/**
 * A screen opened from another one (Settings, a file, the tasks): a chrome band with ← and its
 * name in the middle, the content on the sheet. Fits both of the shell's places for it, as [Screen]
 * does: a whole phone screen, or the pane beside the sidebar (its button, its insets, its notice).
 * The sheet runs under the navigation bar; a list leaves [navBarRoom] below its last row.
 */
@Composable
fun Page(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val sides = LocalScreenInsets.current
    ChromeScreen(
        header = {
            ChromeHeader(title = title, leading = { PageLeading(onBack) }, trailing = actions)
            LocalScreenNotice.current?.invoke()
        },
        modifier = modifier.background(RelayTheme.colors.chrome)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(sides).only(WindowInsetsSides.Horizontal)),
    ) {
        Column(Modifier.fillMaxSize().imePadding(), content = content)
    }
}

/** The sidebar's button where the shell has one, then ←. */
@Composable
private fun PageLeading(onBack: (() -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        LocalPaneToggle.current?.invoke()
        if (onBack != null) CircleButton(TablerIcons.Outline.ArrowLeft, "Back", onBack, tone = CircleTone.Raised)
    }
}

/** The navigation bar's height, for what scrolls under it. */
@Composable
fun navBarRoom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
