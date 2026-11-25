package dev.relay.android.features.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.relay.android.features.git.DiffCounts
import dev.relay.android.features.git.ListNote
import dev.relay.android.features.git.operationInProgress
import dev.relay.android.features.git.rowMeta
import dev.relay.android.features.home.ListedDraft
import dev.relay.android.features.home.ThreadRow
import dev.relay.android.features.home.computerLine
import dev.relay.android.features.home.remoteSitesLine
import dev.relay.android.features.home.tasksLine
import dev.relay.android.features.home.threadDraft
import dev.relay.android.lib.navigation.Route
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteProjectComputer
import dev.relay.android.remote.model.WorkingTree
import dev.relay.android.remote.tunnel.SiteAddress
import dev.relay.android.ui.ChromePage
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.ComposerPill
import dev.relay.android.ui.ComposerPlaceholder
import dev.relay.android.ui.Group
import dev.relay.android.ui.Icon
import dev.relay.android.ui.RowPatch
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.Text
import dev.relay.android.ui.listPadding
import dev.relay.android.ui.pressable
import dev.relay.android.ui.ribbon.RibbonRefreshBox
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronRight
import io.github.lyxnx.compose.ui.tablericons.outline.Edit
import io.github.lyxnx.compose.ui.tablericons.outline.FileCode
import io.github.lyxnx.compose.ui.tablericons.outline.GitBranch
import io.github.lyxnx.compose.ui.tablericons.outline.GitCommit
import io.github.lyxnx.compose.ui.tablericons.outline.GitCompare
import io.github.lyxnx.compose.ui.tablericons.outline.Terminal2
import io.github.lyxnx.compose.ui.tablericons.outline.World

/** What the project's page shows besides its threads. */
internal data class ProjectFacts(
    val id: String,
    val name: String?,
    val computer: RemoteProjectComputer?,
    /** A Git checkout, so it has changes, branches and history. */
    val git: Boolean,
    val tree: WorkingTree?,
    val tasks: Int?,
    val ports: List<Int>,
    /** Whether localhost sites open here, through the phone's tunnel. */
    val tunnels: Boolean,
    /** The computer the sites are opened on. */
    val host: String?,
)

/** A project's checkout, files and tasks as one group, then its threads; a pill to start another floats under. */
@Composable
internal fun ProjectPage(
    facts: ProjectFacts,
    chats: List<RemoteChatSummary>,
    drafts: Map<String, ListedDraft>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onBack: (() -> Unit)?,
    open: (Route) -> Unit,
) {
    val name = facts.name ?: "Project"
    val newThread = { open(Route.NewThread(projectId = facts.id)) }
    ChromePage(
        name,
        subtitle = computerLine(facts.computer),
        onBack = onBack,
        floating = { NewThreadPill(name, newThread) },
    ) { bottom ->
        RibbonRefreshBox(refreshing, onRefresh, Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(top = 16.dp, bottom = bottom)) {
                item(key = "entries") { Entries(facts, open) }
                item(key = "threads-head") { SectionLabel("Threads", Modifier.padding(top = 8.dp), count = chats.size.takeIf { it > 0 }?.toString()) }
                items(chats, key = { it.id }) { chat ->
                    ThreadRow(chat, threadDraft(drafts, chat.id), { open(Route.Thread(chat.id)) })
                }
                if (chats.isEmpty()) item(key = "none") { ListNote("No threads in this project yet.") }
            }
        }
    }
}

@Composable
private fun Entries(facts: ProjectFacts, open: (Route) -> Unit) {
    val tree = facts.tree
    val id = facts.id
    Group(Modifier.padding(horizontal = 8.dp)) {
        if (facts.git) {
            item {
                Entry(
                    TablerIcons.Outline.GitCompare, "Changes", tree?.let(::changesLine),
                    trailing = { if (tree != null) DiffCounts(tree.lines.additions, tree.lines.deletions) },
                ) { open(Route.Changes(id)) }
            }
            item {
                Entry(
                    TablerIcons.Outline.GitBranch,
                    tree?.branch?.ifEmpty { null } ?: "Branch",
                    tree?.operation?.let(::operationInProgress) ?: "Switch or create a branch",
                    subtitleColor = if (tree?.operation != null) RelayTheme.colors.danger else RelayTheme.colors.inkSoft,
                ) { open(Route.Branches(id)) }
            }
            item { Entry(TablerIcons.Outline.GitCommit, "History") { open(Route.History(id)) } }
        }
        item { Entry(TablerIcons.Outline.FileCode, "Files") { open(Route.Files(id)) } }
        item { Entry(TablerIcons.Outline.Terminal2, "Tasks", trailing = { Meta(tasksLine(facts.tasks, facts.computer)) }) { open(Route.Tasks(id)) } }
        if (facts.tunnels) item { Sites(facts, open) }
    }
}

@Composable
private fun Sites(facts: ProjectFacts, open: (Route) -> Unit) {
    val away = facts.computer
    if (away != null) return Entry(TablerIcons.Outline.World, "Sites", remoteSitesLine(facts.ports, away), Modifier.alpha(0.5f), onClick = null)
    val ports = facts.ports.joinToString(", ") { "localhost:$it" }
    val none = "Open localhost on ${facts.host ?: "the computer"}".takeIf { facts.ports.isEmpty() }
    Entry(TablerIcons.Outline.World, "Sites", none, trailing = { Meta(ports.ifEmpty { null }) }) {
        open(Route.Browser(facts.ports.singleOrNull()?.let { SiteAddress(it).href }))
    }
}

@Composable
private fun Meta(text: String?) {
    if (text != null) Text(text, Modifier.widthIn(max = 200.dp), style = rowMeta, color = RelayTheme.colors.inkSoft, maxLines = 1)
}

@Composable
private fun Entry(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    subtitleColor: Color = RelayTheme.colors.inkSoft,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)?,
) {
    val c = RelayTheme.colors
    RowPatch(onClick, modifier, minHeight = if (subtitle == null) 48.dp else 60.dp) {
        Icon(icon, null, tint = c.inkSoft, size = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = RelayTheme.type.listTitle, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            if (subtitle != null) Text(subtitle, style = rowMeta, color = subtitleColor, maxLines = 1)
        }
        trailing()
        if (onClick != null) Icon(TablerIcons.Outline.ChevronRight, null, tint = c.inkFaint, size = 16.dp)
    }
}

/** Looks like the home's composer and opens the one on the new-thread screen, with this project picked. */
@Composable
private fun NewThreadPill(project: String, onClick: () -> Unit) {
    ComposerPill(
        field = { ComposerPlaceholder("New thread in $project") },
        modifier = Modifier.fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "New thread"
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
            .pressable(onClick),
        trailing = {
            CircleButton(TablerIcons.Outline.Edit, "New thread", onClick, size = CircleSize.Composer, tone = CircleTone.Strong)
        },
    )
}
