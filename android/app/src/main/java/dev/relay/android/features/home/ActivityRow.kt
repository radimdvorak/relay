package dev.relay.android.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ProviderIcon
import dev.relay.android.ui.RowTextInset
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.CalendarClock
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronDown
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronRight
import io.github.lyxnx.compose.ui.tablericons.outline.Pencil
import io.github.lyxnx.compose.ui.tablericons.outline.Rotate
import androidx.compose.material3.Text as MaterialText

/** The threads a lead's agent started, listed under it unless folded. */
class RowFamily(val started: List<RemoteChatSummary>, val open: Boolean, val onFold: () -> Unit)

private val MarkSize = 28.dp
private val MarkGap = 14.dp

/** How far a started thread's row moves in, so its title lines up with its lead's. */
internal val StartedIndent = MarkSize + MarkGap

/** One open thread in Activity: where it runs, its title, what it's doing, and how long. */
@Composable
fun ActivityRow(
    chat: RemoteChatSummary,
    projectId: String,
    projectName: String,
    draft: ListedDraft?,
    unread: Boolean,
    selected: Boolean,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    family: RowFamily? = null,
    /** A started thread under its lead: no project line, its state beside the title. */
    compact: Boolean = false,
) {
    // A question in a started thread is the lead's to bring up while they're folded together.
    val familyAsks = family?.started?.any { it.waiting == true } == true
    val shown = if (familyAsks) chat.copy(waiting = true) else chat
    val news = unread || shown.waiting == true
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = if (pressed || selected) RelayTheme.colors.selected else RelayTheme.colors.surface
    val row = modifier.fillMaxWidth().clip(RelayTheme.shapes.row).background(fill)
        .semantics { this.selected = selected }
        .pressable(onClick = onClick, onLongClick = onLongClick, scale = false, interaction = interaction)
    if (compact) return CompactRow(shown, unread, draft != null, news, now, row)
    Row(
        row.heightIn(min = 64.dp).padding(horizontal = RowTextInset, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(MarkGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProjectMark(projectId, projectName, chat.provider, fill)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RowTitle(chat.title, news, Modifier.weight(1f))
                RowTime(shown, unread, now)
            }
            Row(
                Modifier.heightIn(min = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    when {
                        family != null -> FamilyLine(projectName, family)
                        draft != null -> DraftLine(draft)
                        else -> StateLine(shown, projectName, now)
                    }
                }
                // The draft's own line carries the pencil.
                StateGlyph(shown, unread, draft = draft != null && family != null)
            }
        }
    }
}

@Composable
private fun CompactRow(chat: RemoteChatSummary, unread: Boolean, draft: Boolean, news: Boolean, now: Long, row: Modifier) {
    Row(
        row.heightIn(min = 48.dp).padding(horizontal = RowTextInset, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            chat.title, Modifier.weight(1f),
            style = RelayTheme.type.body.copy(fontWeight = if (news) FontWeight.Medium else FontWeight.Normal), maxLines = 1,
        )
        StateGlyph(chat, unread, draft)
        RowTime(chat, unread, now)
    }
}

/** News stands out by weight; a thread that's been read keeps the full ink. */
@Composable
private fun RowTitle(title: String, news: Boolean, modifier: Modifier = Modifier) = Text(
    title, modifier,
    style = RelayTheme.type.listTitle.copy(fontWeight = if (news) FontWeight.Medium else FontWeight.Normal), maxLines = 1,
)

/** "relay · needs input · fix/rotate", the state in its own colour. */
@Composable
private fun StateLine(chat: RemoteChatSummary, projectName: String, now: Long) {
    val c = RelayTheme.colors
    val state = threadState(chat, now)
    val line = buildAnnotatedString {
        append(projectName)
        if (state != null) {
            append(" · ")
            withStyle(SpanStyle(color = state.tone.color(c))) { append(state.text) }
        }
        val branch = if (chat.scope == "pr") "Pull request" else chat.branch
        if (branch != null) append(" · $branch")
    }
    MaterialText(line, style = RelayTheme.type.listMeta, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

internal enum class StateTone { Quiet, Waiting, Limit, News }

private fun StateTone.color(c: RelayColors): Color = when (this) {
    StateTone.Quiet -> c.inkSoft
    StateTone.Waiting -> c.waiting
    StateTone.Limit -> c.warning
    StateTone.News -> c.accent
}

internal data class ThreadState(val text: String, val tone: StateTone)

/** What's going on, for the line under the title; the time beside it says how long. */
internal fun threadState(chat: RemoteChatSummary, now: Long): ThreadState? {
    val limit = chat.limitResume
    return when {
        inputBlocksThread(chat) -> ThreadState("needs input", StateTone.Waiting)
        chat.running == true -> ThreadState(if (chat.goal?.status == "active") "working on its goal" else "working", StateTone.Quiet)
        limit != null && limit.off == true -> ThreadState("usage limit", StateTone.Limit)
        limit != null -> ThreadState("usage limit, resumes ${wakeLabel(limit.at, now)}", StateTone.Limit)
        wokeUnread(chat, now) -> ThreadState("woke up", StateTone.News)
        !chat.pending.isNullOrEmpty() -> ThreadState("waiting on agents", StateTone.Quiet)
        chat.nextSend != null -> ThreadState("sends ${wakeLabel(chat.nextSend, now)}", StateTone.Quiet)
        else -> null
    }
}

@Composable
private fun FamilyLine(projectName: String, family: RowFamily) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$projectName · ", style = RelayTheme.type.listMeta, color = RelayTheme.colors.inkSoft, maxLines = 1)
        FamilyFold(family)
    }
}

/**
 * The lead's "› 4 threads · all done", which folds its started threads; the rest of the row
 * opens the lead. Compose would grow this one line to a 48dp target that covers the title.
 */
@Composable
private fun FamilyFold(family: RowFamily) {
    val c = RelayTheme.colors
    val config = LocalViewConfiguration.current
    val shortTarget = remember(config) {
        object : ViewConfiguration by config {
            override val minimumTouchTargetSize = DpSize(48.dp, 24.dp)
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides shortTarget) {
        Row(
            Modifier.pressable(onClick = family.onFold, scale = false),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (family.open) TablerIcons.Outline.ChevronDown else TablerIcons.Outline.ChevronRight,
                if (family.open) "Fold the threads it started" else "Show the threads it started",
                tint = c.inkSoft,
                size = 14.dp,
            )
            Text(familyLine(family.started), style = RelayTheme.type.listMeta, color = c.inkSoft, maxLines = 1)
        }
    }
}

/** The project's icon with the thread's agent in its corner, cut out by a ring of the row's own fill. */
@Composable
private fun ProjectMark(projectId: String, projectName: String, provider: String?, fill: Color) {
    Box {
        ProjectCardBadge(projectId, projectName, size = MarkSize)
        if (provider != null) Box(
            Modifier.align(Alignment.BottomEnd).offset(5.dp, 5.dp).size(16.dp).background(fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) { ProviderIcon(provider, RelayTheme.colors.inkSoft, size = 11.dp) }
    }
}

/** Under the time: working, needs you, a draft or unread; nothing once it's all seen. */
@Composable
private fun StateGlyph(chat: RemoteChatSummary, unread: Boolean, draft: Boolean) {
    val c = RelayTheme.colors
    val blocked = inputBlocksThread(chat)
    val working = chat.running == true || agentsSince(chat.pending) != null
    if (!blocked && !working && !draft && !unread) return
    // One width for all of them, so dots and spinners line up down the list.
    Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) {
        when {
            blocked -> Dot(c.waiting, "Needs you")
            working -> Spinner(size = 14.dp, color = c.inkSoft)
            draft -> Icon(TablerIcons.Outline.Pencil, null, tint = c.waiting, size = 14.dp)
            else -> Dot(c.accent, "Unread")
        }
    }
}

/** How long it's been working, else how old it is. */
@Composable
private fun RowTime(chat: RemoteChatSummary, unread: Boolean, now: Long) {
    val c = RelayTheme.colors
    val style = timeStyle()
    val since = if (chat.running == true) chat.runningSince else agentsSince(chat.pending)
    when {
        since != null -> Elapsed(since)
        chat.nextSend != null && !unread -> Icon(TablerIcons.Outline.CalendarClock, "Sends later", tint = c.inkSoft, size = 14.dp)
        else -> Text(shortAge(chat.updated, now), style = style, color = c.inkSoft, maxLines = 1)
    }
}

@Composable
private fun timeStyle() = RelayTheme.type.listMeta.copy(fontFeatureSettings = "tnum")

/** "4m 12s"; ticks by itself so only this label redraws. */
@Composable
private fun Elapsed(since: Long) {
    val now = rememberNow(1_000)
    Text(elapsedLabel(since, now), style = timeStyle(), color = RelayTheme.colors.inkSoft, maxLines = 1)
}

@Composable
private fun Dot(color: Color, meaning: String, size: Dp = 8.dp) =
    Box(Modifier.size(size).background(color, CircleShape).semantics { contentDescription = meaning })

/** A shelf's thread, or a settled lead whose started threads still show: one line, with the way back beside it. */
@Composable
fun ShelfRow(
    chat: RemoteChatSummary,
    projectName: String,
    label: String,
    selected: Boolean,
    actionIcon: ImageVector,
    actionLabel: String,
    onClick: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = RelayTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier.fillMaxWidth().clip(RelayTheme.shapes.row).background(if (pressed || selected) c.selected else Color.Transparent)
            .semantics { this.selected = selected }
            .pressable(onClick = onClick, scale = false, interaction = interaction)
            .heightIn(min = 52.dp).padding(start = RowTextInset, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(MarkGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProjectCardBadge(chat.projectId, projectName, size = MarkSize)
        Text(chat.title, Modifier.weight(1f), style = RelayTheme.type.listTitle, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = timeStyle(), color = c.inkSoft, maxLines = 1)
            Box(
                Modifier.semantics { contentDescription = actionLabel }.pressable(onClick = onAction).size(44.dp),
                contentAlignment = Alignment.Center,
            ) { Icon(actionIcon, null, tint = c.inkSoft, size = 18.dp) }
        }
    }
}

/** The unsettle button's glyph, Lucide's RotateCcw. */
val UnsettleIcon get() = TablerIcons.Outline.Rotate
