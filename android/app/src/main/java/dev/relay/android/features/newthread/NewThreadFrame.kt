package dev.relay.android.features.newthread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.ChromeHeader
import dev.relay.android.ui.ChromeScreen
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.LocalPaneToggle
import dev.relay.android.ui.LocalScreenInsets
import dev.relay.android.ui.LocalScreenNotice
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft

/** The new-thread screen's chrome and sheet. The sheet reaches under the navigation bar; its content stays above it and the keyboard. */
@Composable
internal fun NewThreadFrame(onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val sides = LocalScreenInsets.current
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
    ChromeScreen(
        modifier = Modifier.background(RelayTheme.colors.chrome)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(sides).exclude(bottom)),
        header = {
            ChromeHeader(
                title = "New thread",
                leading = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LocalPaneToggle.current?.invoke()
                        if (onBack != null) CircleButton(TablerIcons.Outline.ArrowLeft, "Back", onBack, tone = CircleTone.Raised)
                    }
                },
            )
        },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(bottom)) {
            LocalScreenNotice.current?.invoke()
            content()
        }
    }
}
