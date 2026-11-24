package dev.relay.android.features.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.ChromeHeader
import dev.relay.android.ui.ChromeScreen
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.LocalPaneToggle
import dev.relay.android.ui.LocalScreenInsets
import dev.relay.android.ui.LocalScreenNotice
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft
import io.github.lyxnx.compose.ui.tablericons.outline.Menu2

/**
 * A conversation's screen: its header on the chrome, the rest on the content sheet, clear of the
 * system bars and the keyboard. The sheet itself runs to the screen's edge behind them.
 */
@Composable
internal fun ThreadChrome(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val safe = WindowInsets.safeDrawing.only(LocalScreenInsets.current)
    ChromeScreen(
        header = {
            Box(Modifier.windowInsetsPadding(safe.only(WindowInsetsSides.Horizontal))) { header() }
            LocalScreenNotice.current?.invoke()
        },
    ) {
        Column(
            modifier.fillMaxSize().windowInsetsPadding(safe.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
            content = content,
        )
    }
}

/**
 * ☰ or ←, then the thread's name over a quiet line of where it works, then [trailing]. The name
 * sits beside the button rather than centred, so a long one keeps all the room there is.
 */
@Composable
internal fun ThreadHeader(
    title: String,
    subtitle: String?,
    onMenu: (() -> Unit)?,
    onBack: (() -> Unit)?,
    onTitle: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    ChromeHeader(leading = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LocalPaneToggle.current?.let { toggle -> Box(Modifier.padding(end = 4.dp)) { toggle() } }
            if (onMenu != null) CircleButton(TablerIcons.Outline.Menu2, "Menu", onMenu, tone = CircleTone.Raised)
            else if (onBack != null) CircleButton(TablerIcons.Outline.ArrowLeft, "Back", onBack, tone = CircleTone.Raised)
            Column(
                Modifier.weight(1f)
                    .then(if (onTitle != null) Modifier.pressable(onClick = onTitle, scale = false) else Modifier)
                    .padding(horizontal = 12.dp),
            ) {
                Text(title, style = RelayTheme.type.meta.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                if (!subtitle.isNullOrEmpty())
                    Text(subtitle, style = RelayTheme.type.small, color = RelayTheme.colors.inkSoft, maxLines = 1)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
    })
}
