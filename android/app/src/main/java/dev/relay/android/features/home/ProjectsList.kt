package dev.relay.android.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteOverview
import dev.relay.android.remote.model.RemoteProject
import dev.relay.android.remote.model.RemoteProjectComputer
import dev.relay.android.ui.EmptyState
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.RowPatch
import dev.relay.android.ui.RowSize
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.StatusDot
import dev.relay.android.ui.Text
import dev.relay.android.ui.Tone
import dev.relay.android.ui.listPadding
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Folder
import io.github.lyxnx.compose.ui.tablericons.outline.Plus

/** Scratchpad threads shown before "Show more", as on the desktop. */
private const val SCRATCH_PAGE = 5

/** Every Scratchpad chat has a folder of its own; together they're one list, newest first. */
fun scratchChats(overview: RemoteOverview): List<RemoteChatSummary> {
    val ids = overview.projects.filter { it.scratch == true }.map { it.id }.toSet()
    return overview.listed.filter { it.projectId in ids }.sortedByDescending { it.updated }
}

/** The desktop's sidebar: projects under their virtual folders, "Projects" first. */
fun projectFolders(projects: List<RemoteProject>): List<Pair<String, List<RemoteProject>>> =
    projects.filter { it.scratch != true }
        .groupBy { it.folder?.takeIf(String::isNotEmpty) ?: "Projects" }
        .toList()
        .sortedWith { (a), (b) ->
            when {
                a == b -> 0
                a == "Projects" -> -1
                b == "Projects" -> 1
                else -> a.compareTo(b)
            }
        }

/** Where a project on another computer lives: "On thinkcenter", or that it can't be reached. */
fun computerLine(computer: RemoteProjectComputer?): String? = computer?.let {
    if (it.gone == true) "${it.name} isn't paired anymore" else "On ${it.name}"
}

/** "1 running", or "1 running on thinkcenter" for a project on another computer. */
fun tasksLine(running: Int?, computer: RemoteProjectComputer? = null): String? = running?.let {
    (if (it > 0) "$it running" else "Nothing running") + computer?.let { c -> " on ${c.name}" }.orEmpty()
}

/** A project on another computer's sites: the ports its tasks opened there, which a phone's tunnel to this one can't reach. */
fun remoteSitesLine(ports: List<Int>, computer: RemoteProjectComputer): String =
    (if (ports.isEmpty()) "On ${computer.name}" else "${ports.joinToString(", ") { "localhost:$it" }} on ${computer.name}") +
        " · can't open from here"

/** "2 working · 5 threads", or "No threads yet"; "On thinkcenter · …" for one on another computer. */
fun projectLine(chats: List<RemoteChatSummary>, computer: RemoteProjectComputer? = null): String {
    val where = computerLine(computer)?.let { "$it · " }.orEmpty()
    if (chats.isEmpty()) return where + "No threads yet"
    val busy = chats.count { it.running == true || it.waiting == true }
    val count = "${chats.size} ${if (chats.size == 1) "thread" else "threads"}"
    return where + (if (busy > 0) "$busy working · " else "") + count
}

/** The Projects tab: the Scratchpad, then each folder's projects. */
@Composable
fun ProjectsList(
    overview: RemoteOverview,
    drafts: Map<String, ListedDraft>,
    selected: String?,
    onOpenChat: (RemoteChatSummary) -> Unit,
    onOpenProject: (RemoteProject) -> Unit,
    onNewScratch: () -> Unit,
    bottom: Dp,
    modifier: Modifier = Modifier,
) {
    val now = rememberNow(30_000)
    var allScratch by rememberSaveable { mutableStateOf(false) }
    val scratch = remember(overview) { scratchChats(overview) }
    val folders = remember(overview.projects) { projectFolders(overview.projects) }
    val byProject = remember(overview.chats) { overview.listed.groupBy { it.projectId } }

    LazyColumn(modifier.fillMaxSize(), contentPadding = listPadding(bottom = bottom)) {
        item(key = "scratch-head") { SectionLabel("Scratchpad") }
        item(key = "scratch-new") {
            ListRow(
                "New Scratchpad chat",
                subtitle = "A chat of its own, outside your projects".takeIf { scratch.isEmpty() },
                leadingIcon = TablerIcons.Outline.Plus,
                size = if (scratch.isEmpty()) RowSize.Default else RowSize.Sm,
                onClick = onNewScratch,
            )
        }
        items(if (allScratch) scratch else scratch.take(SCRATCH_PAGE), key = { it.id }, contentType = { "thread" }) { chat ->
            ThreadRow(chat, threadDraft(drafts, chat.id), onClick = { onOpenChat(chat) }, selected = chat.id == selected)
        }
        if (scratch.size > SCRATCH_PAGE) item(key = "scratch-more") {
            ListRow(
                if (allScratch) "Show less" else "Show ${scratch.size - SCRATCH_PAGE} more",
                size = RowSize.Sm,
                titleColor = RelayTheme.colors.inkSoft,
                onClick = { allScratch = !allScratch },
            )
        }
        for ((title, projects) in folders) {
            item(key = "folder:$title") { SectionLabel(title, Modifier.padding(top = 8.dp), count = "${projects.size}") }
            items(projects, key = { "project:${it.id}" }, contentType = { "project" }) { p ->
                ProjectRow(p, byProject[p.id].orEmpty(), now, selected = p.id == selected) { onOpenProject(p) }
            }
        }
        if (folders.isEmpty()) item(key = "no-projects") {
            EmptyState("No projects yet", description = "Add a folder in Relay on your computer and it shows up here.", icon = TablerIcons.Outline.Folder)
        }
    }
}

/** A project: its icon, its name over what goes on in it, when it last moved, and a dot while an agent works. */
@Composable
private fun ProjectRow(project: RemoteProject, chats: List<RemoteChatSummary>, now: Long, selected: Boolean, onClick: () -> Unit) {
    val c = RelayTheme.colors
    val meta = RelayTheme.type.meta.copy(fontSize = 13.sp)
    // Its computer isn't paired anymore, so nothing in it opens until it is.
    val gone = project.computer?.gone == true
    RowPatch(onClick, selected = selected) {
        Box(Modifier.size(22.dp).alpha(if (gone) 0.5f else 1f), contentAlignment = Alignment.Center) { ProjectIcon(project, size = 22.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                project.name,
                style = RelayTheme.type.row,
                color = if (gone) c.inkSoft else c.ink,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(projectLine(chats, project.computer), style = meta, color = c.inkSoft, maxLines = 1)
        }
        if (chats.isNotEmpty()) Text(shortAge(chats.maxOf { it.updated }, now), style = meta, color = c.inkSoft, maxLines = 1)
        when {
            chats.any { it.waiting == true } -> StatusDot(Tone.Waiting, Modifier.semantics { contentDescription = "Waiting for you" })
            chats.any { it.running == true } -> StatusDot(Tone.Info, Modifier.semantics { contentDescription = "Working" })
        }
    }
}
