package dev.relay.android.app

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleTone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import dev.relay.android.features.home.HomeScreen
import dev.relay.android.features.home.HomeTab
import dev.relay.android.features.pulls.PullsList
import dev.relay.android.features.updates.ComputerUpdateRow
import dev.relay.android.features.updates.UpdateBanners
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.ui.LocalPaneToggle
import dev.relay.android.ui.LocalScreenInsets
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.LayoutSidebar
import kotlin.math.min

private const val MIN_SIDEBAR = 240f

/** The sidebar's bounds for a window, as the Expo app's panes.tsx sets them. */
internal fun sidebarBounds(windowDp: Float): Pair<Float, Float> =
    MIN_SIDEBAR to min(560f, windowDp - 360f).coerceAtLeast(MIN_SIDEBAR)

internal fun defaultSidebarWidth(windowDp: Float): Float = (windowDp * 0.36f).coerceIn(280f, 360f)

/** Home with what other features put on it: the update banners, the computers sheet's update row and the pull requests. */
@Composable
fun AppHome(container: AppContainer, shell: ShellState) {
    val navigator = LocalNavigator.current
    HomeScreen(
        container.remote, container.home, container.computerSeen,
        // Beside the side pane, which lists projects and pull requests itself, Home is the threads.
        tab = if (shell.sidePane) HomeTab.Threads else shell.tab,
        onTab = { shell.tab = it },
        bar = !shell.sidePane,
        banners = { UpdateBanners(container.updates, container.remote, Modifier.padding(horizontal = 16.dp)) },
        computerRows = { close -> ComputerUpdateRow(container.updates, container.remote, close) },
        pulls = { bottom -> PullsList(container.remote, query = "", selected = null, bottom, navigator::push) },
    )
}

/**
 * On a wide window: the side pane (resizable by its edge, hideable from the
 * detail pane's header) beside the detail. On a phone only the detail.
 * The detail keeps its place in the tree either way, so turning a foldable
 * doesn't rebuild the stack's screens.
 */
@Composable
fun WideLayout(
    wide: Boolean,
    prefs: ShellPrefs,
    sidebar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    detail: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val windowDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val (minW, maxW) = sidebarBounds(windowDp)
    var live by remember { mutableStateOf<Float?>(null) }
    val width = (live ?: prefs.sidebarWidth ?: defaultSidebarWidth(windowDp)).coerceIn(minW, maxW)
    val showSidebar = wide && !prefs.sidebarHidden

    Row(modifier.fillMaxSize().background(RelayTheme.colors.chrome)) {
        if (showSidebar) {
            Box(Modifier.width(width.dp).fillMaxHeight()) {
                CompositionLocalProvider(LocalScreenInsets provides WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom) {
                    sidebar()
                }
                val interaction = remember { MutableInteractionSource() }
                val dragged by interaction.collectIsDraggedAsState()
                val grip by animateFloatAsState(if (dragged) 1f else 0f, RelayTheme.motion.standard.defaultEffectsSpec(), label = "pane-grip")
                Box(
                    Modifier.align(Alignment.CenterEnd).size(4.dp, 48.dp).graphicsLayer { alpha = grip }
                        .background(RelayTheme.colors.inkFaint, RelayTheme.shapes.circle),
                )
                Box(
                    Modifier.align(Alignment.CenterEnd).width(12.dp).fillMaxHeight().draggable(
                        rememberDraggableState { dx -> live = ((live ?: width) + dx / density.density).coerceIn(minW, maxW) },
                        Orientation.Horizontal,
                        interactionSource = interaction,
                        onDragStopped = {
                            live?.let(prefs::saveSidebarWidth)
                            live = null
                        },
                    ),
                )
            }
        }
        // Clipped: a screen sliding out to the left would otherwise draw over the sidebar.
        Box(Modifier.weight(1f).fillMaxHeight().then(if (showSidebar) Modifier.clipToBounds() else Modifier)) {
            val toggle: (@Composable () -> Unit)? = if (!wide) null else {
                {
                    CircleButton(
                        TablerIcons.Outline.LayoutSidebar,
                        if (prefs.sidebarHidden) "Show threads" else "Hide threads",
                        { prefs.hideSidebar(!prefs.sidebarHidden) },
                        tone = CircleTone.Raised,
                    )
                }
            }
            CompositionLocalProvider(
                LocalScreenInsets provides if (showSidebar) WindowInsetsSides.End + WindowInsetsSides.Top + WindowInsetsSides.Bottom else LocalScreenInsets.current,
                LocalPaneToggle provides toggle,
            ) { detail() }
        }
    }
}
