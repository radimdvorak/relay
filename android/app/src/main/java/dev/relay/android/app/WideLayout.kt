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
import dev.relay.android.features.pulls.PullsList
import dev.relay.android.features.pulls.pullSelection
import dev.relay.android.features.updates.ComputerUpdateRow
import dev.relay.android.features.updates.UpdateBanners
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.Route
import dev.relay.android.ui.ChromeHeader
import dev.relay.android.ui.ChromeScreen
import dev.relay.android.ui.EmptyGreeting
import dev.relay.android.ui.LocalPaneToggle
import dev.relay.android.ui.LocalScreenInsets
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.LayoutSidebar
import kotlin.math.min

private const val MIN_SIDEBAR = 240f

/** Chrome between the two sheets, which are the same colour. */
private val PANE_GAP = 8.dp

/** The sidebar's bounds for a window, as the Expo app's panes.tsx sets them. */
internal fun sidebarBounds(windowDp: Float): Pair<Float, Float> =
    MIN_SIDEBAR to min(560f, windowDp - 360f).coerceAtLeast(MIN_SIDEBAR)

internal fun defaultSidebarWidth(windowDp: Float): Float = (windowDp * 0.36f).coerceIn(280f, 360f)

/** Home beside the detail, marking what the detail has open. */
@Composable
fun HomePane(container: AppContainer, shell: ShellState) {
    val selected = when (val open = LocalNavigator.current.stack.getOrNull(1)) {
        is Route.Thread -> open.id
        is Route.Project -> open.id
        is Route.Pull -> pullSelection(open)
        else -> null
    }
    AppHome(container, shell, pane = true, selected = selected)
}

/** What the detail shows with nothing open beside Home. */
@Composable
fun PaneEmpty() {
    ChromeScreen(
        header = { ChromeHeader(leading = LocalPaneToggle.current) },
        modifier = Modifier.background(RelayTheme.colors.chrome),
    ) { EmptyGreeting(Modifier.align(Alignment.Center)) }
}

/** Home with what other features put on it: the update banners, the computers sheet's update row and the pull requests. */
@Composable
fun AppHome(container: AppContainer, shell: ShellState, pane: Boolean, selected: String?) {
    val navigator = LocalNavigator.current
    val open: (Route) -> Unit = if (pane) navigator::openInPane else navigator::push
    HomeScreen(
        container.remote, container.home, container.computerSeen,
        tab = shell.tab,
        onTab = { shell.tab = it },
        pane = pane,
        selected = selected,
        open = open,
        banners = { UpdateBanners(container.updates, container.remote, Modifier.padding(horizontal = 16.dp)) },
        computerRows = { close -> ComputerUpdateRow(container.updates, container.remote, close) },
        pulls = { bottom -> PullsList(container.remote, query = "", selected = selected, bottom, open) },
    )
}

/**
 * On a wide window: Home in a pane (resizable by its edge, hideable from the
 * detail pane's header) beside the detail. On a phone only the detail.
 * The detail keeps its place in the tree either way, so turning a foldable
 * doesn't rebuild the stack's screens.
 */
@Composable
fun WideLayout(
    wide: Boolean,
    prefs: ShellPrefs,
    sidebar: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val windowDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val (minW, maxW) = sidebarBounds(windowDp)
    var live by remember { mutableStateOf<Float?>(null) }
    val width = (live ?: prefs.sidebarWidth ?: defaultSidebarWidth(windowDp)).coerceIn(minW, maxW)
    val showSidebar = wide && !prefs.sidebarHidden

    Row(Modifier.fillMaxSize().background(RelayTheme.colors.chrome)) {
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
        Box(Modifier.weight(1f).fillMaxHeight().then(if (showSidebar) Modifier.padding(start = PANE_GAP).clipToBounds() else Modifier)) {
            val toggle: (@Composable () -> Unit)? = if (!wide) null else {
                {
                    CircleButton(
                        TablerIcons.Outline.LayoutSidebar,
                        if (prefs.sidebarHidden) "Show list" else "Hide list",
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
