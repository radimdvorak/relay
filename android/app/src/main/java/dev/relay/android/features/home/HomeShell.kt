package dev.relay.android.features.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.ChromeScreen
import dev.relay.android.ui.KeepToastsAbove
import dev.relay.android.ui.LocalScreenInsets
import dev.relay.android.ui.theme.RelayTheme

/**
 * Home's frame: [header] on the chrome, the tab's [page] on the content sheet, [dock] floating
 * over its end. A page gets the room the dock takes, to leave free under its last row; changing
 * tabs fades one page into the next, each keeping its scroll.
 */
@Composable
internal fun HomeShell(
    tab: HomeTab,
    modifier: Modifier = Modifier,
    /** Beside the detail: what floats over that has nothing of this to clear. */
    pane: Boolean = false,
    header: @Composable () -> Unit,
    dock: @Composable (Modifier, onHeight: (Dp) -> Unit) -> Unit,
    page: @Composable ColumnScope.(tab: HomeTab, bottom: Dp) -> Unit,
) {
    var dockHeight by remember { mutableStateOf(0.dp) }
    if (!pane) KeepToastsAbove(dockHeight)
    val underBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pages = rememberSaveableStateHolder()
    val effects = RelayTheme.motion.standard.defaultEffectsSpec<Float>()
    val sides = LocalScreenInsets.current
    ChromeScreen(
        header = { header() },
        modifier = modifier.background(RelayTheme.colors.chrome)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(sides).only(WindowInsetsSides.Horizontal)),
    ) {
        AnimatedContent(
            tab,
            Modifier.fillMaxSize(),
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            label = "home-page",
        ) { shown ->
            pages.SaveableStateProvider(shown) {
                Column(Modifier.fillMaxSize()) { page(shown, dockHeight + underBar + DOCK_FADE) }
            }
        }
        dock(Modifier.align(Alignment.BottomCenter)) { dockHeight = it }
    }
}
