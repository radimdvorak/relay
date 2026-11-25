package dev.relay.android.features.tunnel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.ui.ChromeScreen
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.CircleSurface
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.Icon
import dev.relay.android.ui.LocalPaneToggle
import dev.relay.android.ui.LocalScreenInsets
import dev.relay.android.ui.LocalScreenNotice
import dev.relay.android.ui.PairShapes
import dev.relay.android.ui.Text
import dev.relay.android.ui.fill
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowRight
import io.github.lyxnx.compose.ui.tablericons.outline.Refresh
import io.github.lyxnx.compose.ui.tablericons.outline.X

internal class BrowserBarState(
    val text: String,
    /** A page is up and the address isn't being edited: the page's buttons show. */
    val showsPage: Boolean,
    val canBack: Boolean,
    val canForward: Boolean,
)

/** The browser's chrome and sheet: the address in the band, the page on the sheet under it. */
@Composable
internal fun BrowserFrame(header: @Composable () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val sides = LocalScreenInsets.current
    ChromeScreen(
        header = {
            header()
            // The shell's line: says when the computer can't be reached.
            LocalScreenNotice.current?.invoke()
        },
        modifier = Modifier.background(RelayTheme.colors.chrome)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(sides).only(WindowInsetsSides.Horizontal)),
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)), content = content)
    }
}

/** Close, the address, and the page's back, forward and reload. */
@Composable
internal fun BrowserBar(
    state: BrowserBarState,
    onText: (String) -> Unit,
    onEdit: () -> Unit,
    onGo: () -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(RelayTheme.colors.chrome).windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp).height(CircleSize.Header.diameter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Its own bar instead of a page's, so the sidebar button has to be drawn here.
        LocalPaneToggle.current?.invoke()
        CircleButton(TablerIcons.Outline.X, "Close", onClose, tone = CircleTone.Raised)
        AddressField(state, onText, onEdit, onGo, onReload)
        if (state.showsPage) BackForward(state, onBack, onForward)
    }
}

@Composable
private fun RowScope.AddressField(
    state: BrowserBarState,
    onText: (String) -> Unit,
    onEdit: () -> Unit,
    onGo: () -> Unit,
    onReload: () -> Unit,
) {
    val c = RelayTheme.colors
    val style = RelayTheme.type.code.copy(fontSize = 14.sp, color = c.ink)
    BasicTextField(
        state.text,
        onText,
        Modifier.weight(1f).onFocusChanged { if (it.hasFocus) onEdit() },
        textStyle = style,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        cursorBrush = SolidColor(c.ink),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().height(CircleSize.Header.diameter)
                    .background(CircleTone.Raised.fill(c), RelayTheme.shapes.circle)
                    .padding(start = 16.dp, end = if (state.showsPage) 8.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (state.text.isEmpty()) Text("localhost:port", style = style, color = c.inkFaint, maxLines = 1)
                    field()
                }
                if (state.showsPage) CircleButton(
                    TablerIcons.Outline.Refresh, "Reload", onReload,
                    size = CircleSize.Small, tone = CircleTone.Clear,
                )
            }
        },
    )
}

@Composable
private fun BackForward(state: BrowserBarState, onBack: () -> Unit, onForward: () -> Unit) {
    val half = Modifier.size(38.dp, CircleSize.Header.diameter)
    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
        CircleSurface(onBack, "Back", half, PairShapes.start, CircleTone.Raised, state.canBack, haptic = null) {
            Icon(TablerIcons.Outline.ArrowLeft, null, size = 20.dp)
        }
        CircleSurface(onForward, "Forward", half, PairShapes.end, CircleTone.Raised, state.canForward, haptic = null) {
            Icon(TablerIcons.Outline.ArrowRight, null, size = 20.dp)
        }
    }
}
