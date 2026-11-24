package dev.relay.android.features.drawer

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.features.home.ComputerName
import dev.relay.android.features.home.ComputerStatusLine
import dev.relay.android.features.home.HomeServices
import dev.relay.android.features.home.ProvideProjectIcons
import dev.relay.android.features.home.rememberNow
import dev.relay.android.remote.Remote
import dev.relay.android.remote.credentials.ComputerSeen
import dev.relay.android.ui.RelayMark
import dev.relay.android.ui.rememberInFront
import dev.relay.android.ui.theme.RelayTheme

/** [DrawerContent] on the computer in use: its name, its threads, and what's open. */
@Composable
fun ThreadDrawer(
    remote: Remote,
    home: HomeServices,
    computerSeen: ComputerSeen,
    openId: String?,
    actions: DrawerActions,
    computerRows: @Composable (close: () -> Unit) -> Unit,
    above: (@Composable ColumnScope.() -> Unit)? = null,
    list: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val overview by remote.overview.collectAsState()
    val seen by home.seen.state.collectAsState()
    val inFront by rememberInFront()
    val now = rememberNow(60_000)
    var shownRecent by rememberSaveable { mutableIntStateOf(RECENT_FIRST) }
    val o = overview
    val threads = remember(o, openId, now) { o?.let { drawerThreads(it.listed, openId, now) } }
    val names = remember(o?.projects) { o?.projects.orEmpty().associate { it.id to it.name } }
    ProvideProjectIcons(home) {
        DrawerContent(
            threads, openId, shownRecent,
            onShowMore = { shownRecent += RECENT_MORE },
            unread = { seen.unread(it, openId, inFront) },
            projectName = { names[it.projectId] ?: "?" },
            actions = actions,
            title = {
                DrawerTitle(
                    name = { ComputerName(remote, computerSeen, RelayTheme.type.greeting.copy(fontSize = 22.sp, lineHeight = 28.sp), computerRows) },
                    status = { ComputerStatusLine(remote, computerSeen) },
                )
            },
            above = above,
            list = list,
        )
    }
}

/** Relay's mark beside the computer's serif name, and how the connection is under it when that's news. */
@Composable
internal fun DrawerTitle(name: @Composable () -> Unit, status: @Composable () -> Unit) {
    Row(
        Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RelayMark(size = 26.dp)
        Column {
            name()
            status()
        }
    }
}
