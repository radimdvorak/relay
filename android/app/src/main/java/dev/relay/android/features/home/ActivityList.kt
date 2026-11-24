package dev.relay.android.features.home

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteProject
import dev.relay.android.ui.Icon
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.RowTextInset
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.Text
import dev.relay.android.ui.listPadding
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronRight
import io.github.lyxnx.compose.ui.tablericons.outline.Sunrise
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

private const val SHELF_PAGE = 30

/** A shelf longer than a page counts that page, so years of threads read "30+". */
internal fun shelfCount(size: Int) = if (size > SHELF_PAGE) "$SHELF_PAGE+" else "$size"

/** Resolves the thread as the desktop left it, or null when it refused (and said why). */
typealias Triage = suspend (RemoteChatSummary, TriageAction) -> JsonObject?

/**
 * The Threads tab: open threads in calm rows by what they need from you, the unsent drafts of new
 * threads above them, and Snoozed and Settled folded away below. With nothing open, the greeting.
 */
@Composable
fun ActivityList(
    chats: List<RemoteChatSummary>,
    projects: List<RemoteProject>,
    drafts: Map<String, ListedDraft>,
    /** A filter narrows [chats], so an empty list says nothing about the rest. */
    filtered: Boolean,
    unread: (RemoteChatSummary) -> Boolean,
    folds: Map<String, Boolean>,
    onFold: (lead: String, open: Boolean) -> Unit,
    bridge: Int,
    selected: String?,
    onOpen: (RemoteChatSummary) -> Unit,
    /** A new thread's draft, to carry on writing. */
    onOpenDraft: (ListedDraft, projectId: String) -> Unit,
    onTriage: Triage,
    /** Room under the list for what floats over it. */
    bottom: Dp,
    modifier: Modifier = Modifier,
) {
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    // Ages and "Sends at" labels move with the clock.
    val now = rememberNow(30_000)
    val byId = remember(projects) { projects.associateBy { it.id } }
    // Threads holding unsent text come first, as on the desktop; new threads' drafts above them.
    val drafted = remember(drafts) { drafts.keys.filter { !it.startsWith("new:") }.map { it.substringBefore(":") }.toSet() }
    val fresh = remember(drafts, byId) { drafts.values.filter { newDraftProject(it.key) in byId }.sortedByDescending { it.at } }
    val sections = draftsFirst(chatActivitySections(chats, now), chats, drafted)
    val families = startedFamilies(sections.active, sections.settled)
    var acting by remember { mutableStateOf<RemoteChatSummary?>(null) }
    val shelves = remember { mutableStateMapOf("snoozed" to 0, "settled" to 0) }

    fun name(chat: RemoteChatSummary) = byId[chat.projectId]?.name ?: "?"
    fun triage(chat: RemoteChatSummary, action: TriageAction) = scope.launch { onTriage(chat, action) }

    /** The same guards as the hold menu: nothing settles while asking, nor with a draft that'd keep it here. */
    fun swipes(chat: RemoteChatSummary): Pair<SwipeAction?, SwipeAction?> {
        if (chat.id in drafted) return null to null
        suspend fun swiped(action: TriageAction, done: String): Boolean {
            val after = onTriage(chat, action) ?: return false
            toasts.show(done, description = chat.title, actionLabel = "Undo", durationMs = 5_000, onAction = {
                scope.launch { onTriage(chat, undoTriage(chat, after)) }
            })
            return true
        }
        val settle = settleAction(chat)?.let { (label, action) ->
            SwipeAction(label) { swiped(action, if (action == TriageAction.Settle) "Settled" else "Settles when done") }
        }
        val snooze = SwipeAction("Snooze · ${snoozePresets(now).first().label}") {
            // Taken now: `now` only ticks every half minute.
            val at = System.currentTimeMillis()
            val until = snoozePresets(at).first().until
            swiped(TriageAction.Snooze(until), "Snoozed until ${wakeLabel(until, at)}")
        }
        return settle to snooze
    }

    @Composable
    fun row(chat: RemoteChatSummary, family: RowFamily? = null, compact: Boolean = false) {
        val (settle, snooze) = swipes(chat)
        SwipeTriage(settle, snooze, blocked = acting != null) {
            ActivityRow(
                chat, chat.projectId, name(chat), threadDraft(drafts, chat.id), unread(chat), chat.id == selected, now,
                onClick = { onOpen(chat) }, onLongClick = { acting = chat }, family = family, compact = compact,
            )
        }
    }

    @Composable
    fun startedUnder(started: List<RemoteChatSummary>) {
        Column(Modifier.padding(start = StartedIndent, bottom = 4.dp).fillMaxWidth()) {
            for (s in started) key(s.id) { row(s, compact = true) }
        }
    }

    fun LazyListScope.shelf(kind: String, label: String, items: List<RemoteChatSummary>) {
        if (items.isEmpty()) return
        val shown = shelves.getValue(kind)
        item("shelf-$kind") {
            ShelfLabel(label, items.size, open = shown > 0, Modifier.animateItem()) { shelves[kind] = if (shown > 0) 0 else SHELF_PAGE }
        }
        items(items.take(shown), key = { "$kind-${it.id}" }) { chat ->
            val snoozed = kind == "snoozed"
            ShelfRow(
                chat, name(chat),
                label = if (snoozed) wakeLabel(chat.snoozedUntil ?: now, now) else shortAge(chat.updated, now),
                selected = chat.id == selected,
                actionIcon = if (snoozed) TablerIcons.Outline.Sunrise else UnsettleIcon,
                actionLabel = if (snoozed) "Wake now" else "Move back to activity",
                onClick = { onOpen(chat) },
                onAction = { triage(chat, if (snoozed) TriageAction.Wake else TriageAction.Unsettle) },
                modifier = Modifier.animateItem(),
            )
        }
        if (items.size > shown && shown > 0) item("more-$kind") {
            Text(
                "Show ${minOf(SHELF_PAGE, items.size - shown)} more",
                Modifier.fillMaxWidth().clip(RelayTheme.shapes.row).pressable(onClick = { shelves[kind] = shown + SHELF_PAGE }, scale = false)
                    .heightIn(min = 48.dp).padding(start = RowTextInset, top = 14.dp),
                style = RelayTheme.type.meta,
                color = RelayTheme.colors.inkSoft,
            )
        }
    }

    @Composable
    fun family(chat: RemoteChatSummary) {
        val started = families.started[chat.id].orEmpty()
        if (chat.id in families.headers) {
            ShelfRow(
                chat, name(chat), "Settled", chat.id == selected, UnsettleIcon, "Move back to activity",
                onClick = { onOpen(chat) }, onAction = { triage(chat, TriageAction.Unsettle) },
            )
            return startedUnder(started)
        }
        // A family's fold as the user left it; until then open while any of it needs a look.
        val open = started.isNotEmpty() && (folds[chat.id] ?: !familySettled(started, unread))
        row(chat, family = if (started.isNotEmpty()) RowFamily(started, open) { onFold(chat.id, !open) } else null)
        AnimatedVisibility(
            open,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) { startedUnder(started) }
    }

    val settledShelf = sections.settled.filter { it.id !in families.headers }
    LazyColumn(modifier, contentPadding = listPadding(bottom = bottom, top = 8.dp)) {
        items(fresh, key = { it.key }) { draft ->
            val projectId = newDraftProject(draft.key)!!
            NewDraftRow(draft, byId.getValue(projectId), Modifier.animateItem()) { onOpenDraft(draft, projectId) }
        }
        for ((label, group) in groupByState(families.top, families.started)) {
            if (group.isEmpty()) continue
            item("section-$label") { SectionLabel(label, Modifier.animateItem(), count = "${group.size}") }
            items(group, key = { it.id }) { chat -> Column(Modifier.animateItem()) { family(chat) } }
        }
        if (sections.active.isEmpty() && fresh.isEmpty()) item("empty") {
            val shelfCount = listOf(sections.snoozed, settledShelf).count { it.isNotEmpty() }
            ActivityEmpty(filtered, Modifier.fillParentMaxHeight(1f - 0.08f * shelfCount))
        }
        shelf("snoozed", "Snoozed", sections.snoozed)
        shelf("settled", "Settled", settledShelf)
    }

    val clipboard = LocalClipboard.current
    acting?.let { chat ->
        ThreadMenu(
            chat, now, bridge,
            onTriage = { triage(chat, it) },
            onCopy = { branch -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Branch", branch))) } },
        ) { acting = null }
    }
}

/**
 * Open threads split as you'd triage them: the ones waiting on you, the ones
 * still working, then the rest. A lead counts as needing you when a thread it
 * started does.
 */
internal fun groupByState(
    top: List<RemoteChatSummary>,
    started: Map<String, List<RemoteChatSummary>>,
): List<Pair<String, List<RemoteChatSummary>>> {
    val needs = mutableListOf<RemoteChatSummary>()
    val running = mutableListOf<RemoteChatSummary>()
    val rest = mutableListOf<RemoteChatSummary>()
    for (chat in top) {
        val family = listOf(chat) + started[chat.id].orEmpty()
        when {
            family.any { inputBlocksThread(it) || it.waiting == true } -> needs += chat
            family.any { it.running == true || agentsSince(it.pending) != null } -> running += chat
            else -> rest += chat
        }
    }
    return listOf("Needs you" to needs, "Running" to running, "Recent" to rest)
}

/** A shelf's label, which folds its rows away. */
@Composable
private fun ShelfLabel(label: String, size: Int, open: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (open) 90f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "shelf-turn")
    SectionLabel(
        label,
        modifier.clip(RelayTheme.shapes.row).pressable(onClick = onClick, scale = false).heightIn(min = 48.dp),
        count = shelfCount(size),
    ) {
        Icon(
            TablerIcons.Outline.ChevronRight, if (open) "Fold $label" else "Show $label",
            Modifier.graphicsLayer { rotationZ = turn }, tint = RelayTheme.colors.inkFaint, size = 14.dp,
        )
    }
}

/** A new thread that isn't sent yet: its project, and what's written so far. */
@Composable
private fun NewDraftRow(draft: ListedDraft, project: RemoteProject, modifier: Modifier, onClick: () -> Unit) {
    val c = RelayTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier.fillMaxWidth().clip(RelayTheme.shapes.row).background(if (pressed) c.selected else Color.Transparent)
            .pressable(onClick = onClick, scale = false, interaction = interaction)
            .heightIn(min = 64.dp).padding(horizontal = RowTextInset, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProjectCardBadge(project.id, project.name, size = 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("New thread in ${project.name}", style = RelayTheme.type.row, maxLines = 1)
            DraftLine(draft, Modifier.heightIn(min = 20.dp))
        }
    }
}
