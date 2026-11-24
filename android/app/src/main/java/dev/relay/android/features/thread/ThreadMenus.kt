package dev.relay.android.features.thread

// The thread's menus and sheets: its own actions, snoozing, side
// conversations, a message's hold menu and renaming.

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import dev.relay.android.features.thread.logic.Clock
import dev.relay.android.features.thread.logic.offersSecondOpinion
import dev.relay.android.features.thread.logic.preview
import dev.relay.android.features.thread.logic.quoteExcerpt
import dev.relay.android.features.thread.logic.quoteMarkdown
import dev.relay.android.features.thread.logic.withoutMention
import dev.relay.android.remote.model.ChatMessage
import dev.relay.android.remote.model.ChatWorktree
import dev.relay.android.remote.model.RemoteChat
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.Bridge
import dev.relay.android.lib.navigation.Route
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.Text
import dev.relay.android.ui.TextField
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.AdjustmentsHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.Alarm
import io.github.lyxnx.compose.ui.tablericons.outline.Archive
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowBackUp
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowsMinimize
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowsSplit
import io.github.lyxnx.compose.ui.tablericons.outline.Bug
import io.github.lyxnx.compose.ui.tablericons.outline.Checks
import io.github.lyxnx.compose.ui.tablericons.outline.Copy
import io.github.lyxnx.compose.ui.tablericons.outline.CornerUpLeft
import io.github.lyxnx.compose.ui.tablericons.outline.FileDiff
import io.github.lyxnx.compose.ui.tablericons.outline.Folder
import io.github.lyxnx.compose.ui.tablericons.outline.GitBranch
import io.github.lyxnx.compose.ui.tablericons.outline.GitCompare
import io.github.lyxnx.compose.ui.tablericons.outline.History
import io.github.lyxnx.compose.ui.tablericons.outline.Mail
import io.github.lyxnx.compose.ui.tablericons.outline.Messages
import io.github.lyxnx.compose.ui.tablericons.outline.Pencil
import io.github.lyxnx.compose.ui.tablericons.outline.Quote
import io.github.lyxnx.compose.ui.tablericons.outline.Refresh
import io.github.lyxnx.compose.ui.tablericons.outline.Robot
import io.github.lyxnx.compose.ui.tablericons.outline.Share
import io.github.lyxnx.compose.ui.tablericons.outline.World

/** What the thread menu acts with; null leaves an item out. */
class ThreadMenuActions(
    val settled: Boolean,
    /** Settling it now waits for the answer to end. */
    val running: Boolean,
    val snoozed: Boolean,
    val branch: String?,
    /** Its own worktree, while it has one. */
    val worktree: ChatWorktree?,
    val sides: Int,
    val onRename: () -> Unit,
    val onTriage: (kind: String, failed: String) -> Unit,
    val onSnooze: () -> Unit,
    /** Left out where the computer can't take it from a phone. */
    val onUnread: (() -> Unit)?,
    val onRegenerate: (() -> Unit)?,
    val onCopy: (String) -> Unit,
    val onSides: () -> Unit,
    val onWorktreeChanges: () -> Unit,
    val onChanges: () -> Unit,
    val onFiles: () -> Unit,
    val onHistory: () -> Unit,
    val onCompact: () -> Unit,
    val onArchive: () -> Unit,
    /** Where the computer can tunnel its localhost to the phone. */
    val onSites: (() -> Unit)? = null,
    val onKeepScroll: (() -> Unit)? = null,
    /** A repository thread, on a computer that can move it. */
    val onSwitchBranch: (() -> Unit)? = null,
    /** What the composer sends with, once it's there: its sheet, and where it stands in a line. */
    val onSettings: (() -> Unit)? = null,
    val settingsLine: String? = null,
)

fun threadMenu(a: ThreadMenuActions): List<MenuItem> = buildList {
    a.onKeepScroll?.let { add(MenuItem("Keep scroll incident", "Preserve recent layouts and scroll decisions; export in Settings.", icon = TablerIcons.Outline.Bug, onPress = it)) }
    add(MenuItem("Rename", icon = TablerIcons.Outline.Pencil, onPress = a.onRename))
    add(
        when {
            a.settled -> MenuItem("Move back to active", icon = TablerIcons.Outline.ArrowBackUp) { a.onTriage("unsettle", "Couldn't move it") }
            a.running -> MenuItem("Mark as done when it ends", icon = TablerIcons.Outline.Checks) { a.onTriage("settle-when-done", "Couldn't mark it done") }
            else -> MenuItem("Mark as done", icon = TablerIcons.Outline.Checks) { a.onTriage("settle", "Couldn't mark it done") }
        },
    )
    add(
        if (a.snoozed) MenuItem("Wake up now", icon = TablerIcons.Outline.Alarm) { a.onTriage("wake", "Couldn't wake it") }
        else MenuItem("Snooze…", icon = TablerIcons.Outline.Alarm, onPress = a.onSnooze),
    )
    a.onUnread?.let { add(MenuItem("Mark as unread", "Back to the list, marked for later", icon = TablerIcons.Outline.Mail, onPress = it)) }
    a.onRegenerate?.let { add(MenuItem("Regenerate title", "A fresh name from the conversation so far", icon = TablerIcons.Outline.Refresh, onPress = it)) }
    a.onSwitchBranch?.let { add(MenuItem("Switch branch…", "Goes wherever the branch is worked on", icon = TablerIcons.Outline.GitBranch, onPress = it)) }
    a.onSettings?.let { add(MenuItem("Model, permissions and usage", a.settingsLine, icon = TablerIcons.Outline.AdjustmentsHorizontal, onPress = it)) }
    a.branch?.let { add(MenuItem("Copy branch name", it, icon = TablerIcons.Outline.Copy) { a.onCopy(it) }) }
    a.worktree?.path?.let { add(MenuItem("Copy worktree path", it, icon = TablerIcons.Outline.Copy) { a.onCopy(it) }) }
    if (a.sides > 0) add(MenuItem("Side conversations", "${a.sides} beside this one", icon = TablerIcons.Outline.Messages, onPress = a.onSides))
    a.worktree?.takeIf { it.path != null }?.let { w ->
        add(
            MenuItem(
                "Worktree changes",
                "Everything ${w.branch ?: "its branch"} has that ${w.from ?: "the checkout"} doesn't yet.",
                icon = TablerIcons.Outline.GitCompare,
                onPress = a.onWorktreeChanges,
            ),
        )
    }
    add(MenuItem("Changes", icon = TablerIcons.Outline.FileDiff, onPress = a.onChanges))
    add(MenuItem("Files", icon = TablerIcons.Outline.Folder, onPress = a.onFiles))
    add(MenuItem("History", icon = TablerIcons.Outline.History, onPress = a.onHistory))
    a.onSites?.let { add(MenuItem("Sites", "What runs on the computer's localhost, in a browser here", icon = TablerIcons.Outline.World, onPress = it)) }
    add(MenuItem("Compact the conversation", "The agent summarises it to free up its context.", icon = TablerIcons.Outline.ArrowsMinimize, onPress = a.onCompact))
    add(MenuItem("Archive", destructive = true, icon = TablerIcons.Outline.Archive, onPress = a.onArchive))
}

/** A message's text as it reads, without the `@agent` a send leads with. */
fun readText(m: ChatMessage) = if (m.role == "user") withoutMention(m.body) else m.body

fun messageMenu(
    m: ChatMessage,
    onCopy: (String) -> Unit,
    /** Only on the main conversation. */
    onReply: (() -> Unit)?,
    onFork: (() -> Unit)?,
    /** Once the composer is there. */
    onQuote: ((String) -> Unit)?,
    onShare: (String) -> Unit,
    /** Where the computer takes it, on a finished answer of the main conversation. */
    onSecondOpinion: (() -> Unit)? = null,
): List<MenuItem> = buildList {
    val text = readText(m)
    add(MenuItem("Copy", icon = TablerIcons.Outline.Copy) { onCopy(text) })
    onReply?.let { add(MenuItem("Reply", "A side conversation that leaves this one as it is.", icon = TablerIcons.Outline.CornerUpLeft, onPress = it)) }
    if (onFork != null && m.role == "assistant" && m.status == "complete" && m.parentId == null && m.side != true)
        add(MenuItem("Fork from here", "A new thread with the conversation up to this answer.", icon = TablerIcons.Outline.ArrowsSplit, onPress = onFork))
    if (onSecondOpinion != null && offersSecondOpinion(m))
        add(MenuItem("Second opinion…", "Another agent checks this answer.", icon = TablerIcons.Outline.Robot, onPress = onSecondOpinion))
    if (text.isBlank()) return@buildList
    onQuote?.let { quote ->
        add(MenuItem("Quote", "The start of this message, into your reply", icon = TablerIcons.Outline.Quote) { quote(quoteMarkdown(quoteExcerpt(text))) })
    }
    add(MenuItem("Share…", icon = TablerIcons.Outline.Share) { onShare(text) })
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun SnoozeSheet(onPick: (Long) -> Unit, onClose: () -> Unit) {
    val clock = remember { Clock() }
    val now = remember { System.currentTimeMillis() }
    MenuSheet(
        clock.snoozePresets(now).map { (label, until) -> MenuItem(label, clock.wakeLabel(until, now)) { onPick(until) } },
        onClose,
        title = "Snooze until",
    )
}

@Composable
fun SidesSheet(sides: List<ChatMessage>, counts: Map<String, Int>, onOpen: (ChatMessage) -> Unit, onClose: () -> Unit) {
    MenuSheet(
        sides.map { m ->
            val n = counts[m.id] ?: 0
            val what = if (m.side == true) "Asked beside the conversation" else if (m.role == "user") "Your message" else "An answer"
            MenuItem(
                preview(withoutMention(m.body), 80).ifEmpty { "Side conversation" },
                "$what · $n ${if (n == 1) "reply" else "replies"}",
            ) { onOpen(m) }
        },
        onClose,
        title = "Side conversations",
    )
}

@Composable
fun RenameSheet(title: String, onSave: suspend (String) -> Unit, onClose: () -> Unit) {
    var value by remember { mutableStateOf(title) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val name = value.trim()
    Sheet(onDismiss = onClose, title = "Rename thread") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(value, { value = it }, placeholder = "Thread name", focusRequester = focus)
            error?.let { Text(it, style = RelayTheme.type.meta, color = RelayTheme.colors.danger) }
            Button(
                "Save",
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            onSave(name)
                            onClose()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: e.toString()
                        } finally {
                            saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Default,
                size = ButtonSize.Lg,
                loading = saving,
                enabled = name.isNotEmpty() && name != title && !saving,
            )
        }
    }
}


/**
 * Where "Switch branch…" goes: the branch list moving this thread, for a
 * repository thread on a computer that can move it; null leaves it out.
 */
fun branchSwitch(bridge: Int, summary: RemoteChatSummary?, thread: RemoteChat?): Route.Branches? {
    if (bridge < Bridge.BRANCH_MOVES || thread == null) return null
    val repositoryThread = summary?.scope == "project" && summary.reviewer != true
    if (!repositoryThread) return null
    val inWorktree = thread.worktree?.path != null
    val branch = if (inWorktree) thread.worktree?.branch else summary?.branch
    return Route.Branches(thread.projectId, thread.id, branch, inWorktree)
}
