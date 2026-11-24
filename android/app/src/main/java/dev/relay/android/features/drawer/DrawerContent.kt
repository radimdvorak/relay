package dev.relay.android.features.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.relay.android.features.home.ProjectCardBadge
import dev.relay.android.features.home.agentsSince
import dev.relay.android.features.home.inputBlocksThread
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ListInset
import dev.relay.android.ui.RowTextInset
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.Text
import dev.relay.android.ui.fill
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Edit
import io.github.lyxnx.compose.ui.tablericons.outline.HelpCircle
import io.github.lyxnx.compose.ui.tablericons.outline.Search
import io.github.lyxnx.compose.ui.tablericons.outline.Settings

/** What the drawer's rows and buttons do. */
class DrawerActions(
    val onThread: (RemoteChatSummary) -> Unit,
    val onSettings: () -> Unit,
    val onSearch: () -> Unit,
    val onNewThread: () -> Unit,
)

/**
 * The drawer's insides, over the phone's screen or as the Fold's side pane: [title], whatever sits
 * on top of the threads ([above]), Pinned and Recent, and settings, search and a new thread below.
 */
@Composable
internal fun DrawerContent(
    threads: DrawerThreads?,
    openId: String?,
    shownRecent: Int,
    onShowMore: () -> Unit,
    unread: (RemoteChatSummary) -> Boolean,
    projectName: (RemoteChatSummary) -> String,
    actions: DrawerActions,
    title: @Composable () -> Unit,
    above: (@Composable ColumnScope.() -> Unit)? = null,
    /** Another list in the threads' place. */
    list: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Start),
        ),
    ) {
        Box(Modifier.fillMaxWidth().padding(start = ListInset + RowTextInset, end = 16.dp, top = 16.dp, bottom = 8.dp)) { title() }
        if (above != null) Column(Modifier.padding(horizontal = ListInset), content = above)
        Box(Modifier.weight(1f)) {
            if (list != null) Column(Modifier.fillMaxSize(), content = list)
            else Threads(threads, openId, shownRecent, onShowMore, unread, projectName, actions, Modifier.fillMaxSize())
            val chrome = RelayTheme.colors.chrome
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(20.dp)
                    .background(Brush.verticalGradient(listOf(chrome.copy(alpha = 0f), chrome))),
            )
        }
        DrawerDock(actions)
    }
}

@Composable
private fun Threads(
    threads: DrawerThreads?,
    openId: String?,
    shownRecent: Int,
    onShowMore: () -> Unit,
    unread: (RemoteChatSummary) -> Boolean,
    projectName: (RemoteChatSummary) -> String,
    actions: DrawerActions,
    modifier: Modifier,
) {
    val c = RelayTheme.colors
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = ListInset, end = ListInset, bottom = 20.dp),
    ) {
        if (threads == null) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { Spinner(color = c.inkSoft) }
            }
            return@LazyColumn
        }
        fun LazyListScope.rows(kind: String, chats: List<RemoteChatSummary>) = items(chats, key = { "$kind-${it.id}" }) { chat ->
            DrawerThread(
                chat, projectName(chat),
                unread = unread(chat),
                selected = chat.id == openId,
                modifier = Modifier.animateItem(),
                onClick = { actions.onThread(chat) },
            )
        }
        if (threads.pinned.isNotEmpty()) {
            item(key = "pinned") { SectionLabel("Pinned") }
            rows("pinned", threads.pinned)
        }
        if (threads.recent.isNotEmpty()) {
            item(key = "recent") { SectionLabel("Recent") }
            rows("recent", threads.recent.take(shownRecent))
        }
        if (threads.recent.size > shownRecent) item(key = "more") {
            Box(
                Modifier.fillMaxWidth().clip(RelayTheme.shapes.row).pressable(onShowMore, scale = false)
                    .height(44.dp).padding(horizontal = RowTextInset),
                contentAlignment = Alignment.CenterStart,
            ) { Text("Show more", style = RelayTheme.type.body, color = c.inkSoft) }
        }
        if (threads.pinned.isEmpty() && threads.recent.isEmpty()) item(key = "none") {
            Text("No threads yet.", Modifier.padding(horizontal = RowTextInset, vertical = 20.dp), style = RelayTheme.type.body, color = c.inkSoft)
        }
    }
}

@Composable
private fun DrawerThread(
    chat: RemoteChatSummary,
    project: String,
    unread: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = RelayTheme.colors
    DrawerRow(chat.title, selected, onClick, modifier, leading = { ProjectCardBadge(chat.projectId, project, size = 20.dp) }) {
        val working = chat.running == true || agentsSince(chat.pending) != null
        when {
            inputBlocksThread(chat) || chat.waiting == true -> Icon(TablerIcons.Outline.HelpCircle, "Needs you", tint = c.waiting, size = 18.dp)
            working -> Spinner(size = 14.dp, color = c.inkSoft)
            unread -> Box(Modifier.size(8.dp).background(c.info, RelayTheme.shapes.circle).semantics { contentDescription = "Unread" })
        }
    }
}

/** A 44dp row: a 20dp [leading] mark, its name, and a small glyph at the end. */
@Composable
internal fun DrawerRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val c = RelayTheme.colors
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    Row(
        modifier.fillMaxWidth().clip(RelayTheme.shapes.row)
            .semantics { this.selected = selected }
            .background(if (selected || pressed) c.selected else Color.Transparent)
            .pressable(onClick, scale = false, interaction = press)
            .heightIn(min = 44.dp).padding(horizontal = RowTextInset, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { leading() }
        Text(title, Modifier.weight(1f), style = RelayTheme.type.body, color = c.ink, maxLines = 1)
        trailing()
    }
}

@Composable
internal fun DrawerRow(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) =
    DrawerRow(title, selected, onClick, leading = { Icon(icon, null, tint = if (selected) RelayTheme.colors.ink else RelayTheme.colors.inkSoft, size = 20.dp) })

/** Settings, a field-shaped way into search, and the dark new-thread circle. */
@Composable
private fun DrawerDock(actions: DrawerActions) {
    val c = RelayTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(TablerIcons.Outline.Settings, "Settings", actions.onSettings, tone = CircleTone.Raised)
        Row(
            Modifier.weight(1f).height(44.dp).clip(RelayTheme.shapes.circle)
                .background(CircleTone.Raised.fill(c))
                .pressable(actions.onSearch, scale = false)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(TablerIcons.Outline.Search, null, tint = c.inkSoft, size = 20.dp)
            Text("Search", style = RelayTheme.type.row, color = c.inkSoft, maxLines = 1)
        }
        CircleButton(TablerIcons.Outline.Edit, "New thread", actions.onNewThread, tone = CircleTone.Strong)
    }
}
