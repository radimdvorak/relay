package dev.relay.android.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import dev.relay.android.features.drawer.DrawerActions
import dev.relay.android.features.drawer.DrawerRow
import dev.relay.android.features.drawer.ThreadDrawer
import dev.relay.android.features.home.HomePage
import dev.relay.android.features.home.HomeTab
import dev.relay.android.features.pulls.PullsList
import dev.relay.android.features.pulls.pullSelection
import dev.relay.android.features.updates.ComputerUpdateRow
import dev.relay.android.lib.navigation.Route
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Folders
import io.github.lyxnx.compose.ui.tablericons.outline.GitPullRequest
import io.github.lyxnx.compose.ui.tablericons.outline.Messages

/** The thread the reader is in, also from a screen opened over it (its changes, a file). */
private fun openThread(stack: List<Route>): String? = stack.firstNotNullOfOrNull { (it as? Route.Thread)?.id }

/** The drawer over a phone's screen: everything picked in it closes it. */
@Composable
fun PhoneDrawer(container: AppContainer, shell: ShellState, navigator: AppNavigator) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focus.clearFocus()
        keyboard?.hide()
    }
    fun go(move: () -> Unit) {
        move()
        shell.drawer.close()
    }
    ThreadDrawer(
        container.remote, container.home, container.computerSeen,
        openId = openThread(navigator.stack),
        actions = DrawerActions(
            onThread = { chat -> go { navigator.switchTo(Route.Thread(chat.id)) } },
            onSettings = { go { navigator.pushFromDrawer(Route.Settings) } },
            onSearch = { go { navigator.pushFromDrawer(Route.Search()) } },
            onNewThread = { go { navigator.pushFromDrawer(Route.NewThread()) } },
        ),
        computerRows = { close -> ComputerUpdateRow(container.updates, container.remote, close) },
    )
}

/**
 * The Fold's inner screen: the drawer stays beside the detail. Home's destinations on top pick its
 * list, so a project or a pull request opens beside its list too; Threads also goes back to Home.
 */
@Composable
fun SidePane(container: AppContainer, shell: ShellState, navigator: AppNavigator) {
    val stack = navigator.stack
    val open = stack.getOrNull(1)
    val selected = when (open) {
        is Route.Project -> open.id
        is Route.Pull -> pullSelection(open)
        else -> openThread(stack)
    }
    ThreadDrawer(
        container.remote, container.home, container.computerSeen,
        openId = openThread(stack),
        actions = DrawerActions(
            onThread = { navigator.switchTo(Route.Thread(it.id)) },
            onSettings = { navigator.switchTo(Route.Settings) },
            onSearch = { navigator.switchTo(Route.Search(pulls = shell.tab == HomeTab.Pulls)) },
            onNewThread = { navigator.switchTo(Route.NewThread()) },
        ),
        computerRows = { close -> ComputerUpdateRow(container.updates, container.remote, close) },
        above = {
            for ((tab, label, icon) in Destinations) DrawerRow(label, icon, selected = tab == shell.tab) {
                shell.tab = tab
                if (tab == HomeTab.Threads) navigator.home()
            }
        },
        list = when (shell.tab) {
            HomeTab.Threads -> null
            HomeTab.Projects -> ({ HomePage(container.remote, container.home, HomeTab.Projects, selected, PANE_BOTTOM, navigator::switchTo) })
            HomeTab.Pulls -> ({ PullsList(container.remote, "", selected, PANE_BOTTOM, navigator::switchTo) })
        },
    )
}

private val PANE_BOTTOM = 12.dp

private val Destinations = listOf(
    Triple(HomeTab.Threads, "Threads", TablerIcons.Outline.Messages),
    Triple(HomeTab.Projects, "Projects", TablerIcons.Outline.Folders),
    Triple(HomeTab.Pulls, "Pull requests", TablerIcons.Outline.GitPullRequest),
)
