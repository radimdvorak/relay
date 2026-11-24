package dev.relay.android.features.newthread

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.lib.navigation.NEW_THREAD_GREETING
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteProject
import dev.relay.android.ui.EmptyGreeting
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ListInset
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.ProjectBadge
import dev.relay.android.ui.RowSize
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.sharedBox
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronDown
import io.github.lyxnx.compose.ui.tablericons.outline.HelpCircle
import io.github.lyxnx.compose.ui.tablericons.outline.Message
import io.github.lyxnx.compose.ui.tablericons.outline.Notebook

// mobile/src/ui/NewThreadWhere.tsx: where a new thread starts and works.

/** A project's badge here; the app hands in Home's, which shows the project's own icon. */
val LocalProjectBadge = staticCompositionLocalOf<@Composable (RemoteProject, Dp) -> Unit> {
    { project, size -> ProjectBadge(project.name, size = size) }
}

/**
 * Over a new thread's composer, in reach of the thumb: the project it starts in and, for a Git
 * project, where it works. Each opens a sheet.
 */
@Composable
fun WhereLine(project: RemoteProject?, picked: Where?, workspace: String, onOpen: (WhereSheet) -> Unit) {
    val c = RelayTheme.colors
    val name = if (picked == SCRATCH) "Scratchpad" else project?.name ?: "Projects…"
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Choice("Project: $name", name, strong = true, Modifier.weight(1f, fill = false), { onOpen(WhereSheet.Project) }) {
            if (picked == SCRATCH) Icon(TablerIcons.Outline.Notebook, null, size = 18.dp, tint = c.inkSoft)
            else if (project != null) LocalProjectBadge.current(project, 18.dp)
        }
        val option = workspaceOption(workspace)
        if (project != null && project.plain != true && option != null) {
            val short = if (option.value == "worktree") "Own worktree" else option.label
            Choice("Works in: ${option.label}", short, strong = false, Modifier, { onOpen(WhereSheet.Workspace) }) {
                Icon(option.icon, null, size = 18.dp, tint = c.inkSoft)
            }
        }
    }
}

/** A choice as plain text with its icon, so the two sit quietly over the composer's own chips. */
@Composable
private fun Choice(
    label: String,
    text: String,
    strong: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val c = RelayTheme.colors
    Row(
        modifier.heightIn(min = 44.dp).semantics { contentDescription = label }.pressable(onClick = onClick, role = Role.Button),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(
            text, Modifier.weight(1f, fill = false),
            style = RelayTheme.type.meta.copy(fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal),
            color = if (strong) c.ink else c.inkSoft,
            maxLines = 1,
        )
        Icon(TablerIcons.Outline.ChevronDown, null, size = 14.dp, tint = c.inkFaint)
    }
}

/** The room the greeting keeps; the project's latest threads take what's left. */
private val GreetingRoom = 150.dp

/** Less than this and a list of threads isn't one. */
private val LeastRecent = 96.dp

/**
 * Over the composer: the greeting, and under it the project's latest open threads for when the one
 * to go on with already exists. The keyboard covers the end of those first.
 */
@Composable
fun NewThreadIntro(
    project: RemoteProject?,
    picked: Where?,
    scratchError: String?,
    recent: List<RemoteChatSummary>,
    onRetryScratch: () -> Unit,
    onOpenRecent: (RemoteChatSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val recentRoom = maxHeight - GreetingRoom
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Greeting(project, picked, scratchError, onRetryScratch)
            }
            if (recent.isNotEmpty() && recentRoom >= LeastRecent)
                RecentThreads(recent, onOpenRecent, Modifier.heightIn(max = recentRoom))
        }
    }
}

@Composable
private fun Greeting(project: RemoteProject?, picked: Where?, scratchError: String?, onRetryScratch: () -> Unit) {
    val c = RelayTheme.colors
    val scratch = picked == SCRATCH
    val failed = scratch && scratchError != null
    val line = when {
        failed -> "$scratchError Tap to try again."
        scratch -> SCRATCH_HINT
        else -> projectPlace(project)
    }
    Column(
        Modifier.fillMaxWidth()
            .then(if (failed) Modifier.pressable(onClick = onRetryScratch, role = Role.Button, scale = false) else Modifier),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyGreeting(
            Modifier.sharedBox(NEW_THREAD_GREETING, RectangleShape),
            text = if (scratch) "Ask anything" else "What should we work on?",
        )
        if (line != null) Text(
            line, Modifier.padding(horizontal = 40.dp),
            style = RelayTheme.type.meta, color = if (failed) c.danger else c.inkSoft, textAlign = TextAlign.Center, maxLines = 3,
        )
    }
}

@Composable
private fun RecentThreads(chats: List<RemoteChatSummary>, onOpen: (RemoteChatSummary) -> Unit, modifier: Modifier) {
    val c = RelayTheme.colors
    val scroll = rememberScrollState()
    Column(modifier.fillMaxWidth().fadeEnd(scroll).verticalScroll(scroll).padding(horizontal = ListInset)) {
        SectionLabel("Or carry on")
        for (chat in chats) ListRow(
            chat.title.ifEmpty { "New thread" },
            size = RowSize.Sm,
            leading = {
                when {
                    chat.waiting == true -> Icon(TablerIcons.Outline.HelpCircle, "Waiting for you", size = 20.dp, tint = c.waiting)
                    chat.running == true -> Spinner(size = 16.dp, color = c.inkSoft)
                    else -> Icon(TablerIcons.Outline.Message, null, size = 20.dp, tint = c.inkSoft)
                }
            },
            onClick = { onOpen(chat) },
        )
    }
}

/** Fades the last rows out while there's more under them. */
private fun Modifier.fadeEnd(scroll: ScrollState) = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (!scroll.canScrollForward) return@drawWithContent
        val fade = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - 32.dp.toPx(), endY = size.height)
        drawRect(fade, blendMode = BlendMode.DstIn)
    }
