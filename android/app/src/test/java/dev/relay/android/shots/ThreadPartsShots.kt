package dev.relay.android.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.features.thread.AgentRun
import dev.relay.android.features.thread.ChecklistSteps
import dev.relay.android.features.thread.GoalStrip
import dev.relay.android.features.thread.LimitStrip
import dev.relay.android.features.thread.LocalReadingBack
import dev.relay.android.features.thread.LocalThreadRemote
import dev.relay.android.features.thread.MenuRows
import dev.relay.android.features.thread.Peek
import dev.relay.android.features.thread.ProgressSteps
import dev.relay.android.features.thread.ProgressStrip
import dev.relay.android.features.thread.QuietCard
import dev.relay.android.features.thread.QueueList
import dev.relay.android.features.thread.SettledStrip
import dev.relay.android.features.thread.StartedRow
import dev.relay.android.features.thread.StoppedStrip
import dev.relay.android.features.thread.SubagentStrip
import dev.relay.android.features.thread.ThreadMenuActions
import dev.relay.android.features.thread.WaitingStrip
import dev.relay.android.features.thread.WatchNotes
import dev.relay.android.features.thread.WrittenFile
import dev.relay.android.features.thread.threadMenu
import dev.relay.android.remote.model.ChatPending
import dev.relay.android.remote.model.ChatScope
import dev.relay.android.remote.model.LimitResume
import dev.relay.android.remote.model.RemoteChat
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteQueued
import dev.relay.android.remote.model.SubagentRun
import dev.relay.android.remote.model.ThreadGoal
import dev.relay.android.remote.model.WrittenCode
import dev.relay.android.remote.model.WrittenEdit
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.RelaySheetFrame
import dev.relay.android.ui.ToastState
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The pieces of a thread the saved copy in [ThreadShots] can't show open. */
class ThreadPartsShots : ShotTest() {
    @get:Rule val folder = TemporaryFolder()

    private fun part(name: String, content: @Composable ColumnScope.() -> Unit) {
        for (dark in listOf(false, true)) Shot(name, dark = dark) { Page(content) }
        Shot("$name-desktop", dark = true) { RelayTheme(desktopDark) { Page(content) } }
    }

    @Composable
    private fun Page(content: @Composable ColumnScope.() -> Unit) {
        val remote = remember { sampleRemote(folder, RemoteChat("t", "relay", "Sample", scope = ChatScope("project"))) }
        CompositionLocalProvider(LocalThreadRemote provides remote, LocalToasts provides remember { ToastState() }, LocalReadingBack provides true) {
            Column(Modifier.fillMaxSize().background(RelayTheme.colors.surface).padding(vertical = 20.dp), content = content)
        }
    }

    @Test
    fun trace() = part("parts-trace") {
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AgentRun("t", ThreadSamples.answer("m1", "", status = "streaming", minutesAgo = 1, trace = ThreadSamples.liveTrace), "/work/relay")
            AgentRun("t", ThreadSamples.answer("m2", "Done.", trace = ThreadSamples.trace + ThreadSamples.answered), "/work/relay", open = true)
        }
    }

    @Test
    fun code() = part("parts-code") {
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WrittenFile(WrittenCode("swap", "Composer.kt", edits = listOf(WrittenEdit("val inset = imeInset()\nval top = 0\n", "val inset = bottom\nval top = 0\nval shown = remember { true }\n"))), named = true)
            WrittenFile(WrittenCode("whole", "notes.kt", content = "fun main() {\n    println(\"hello\")\n}"))
            val notes = listOf(
                """{"id":"n1","tag":"Risk","line":"The retry runs the migration twice when the first try times out.","title":"Why","points":["One"]}""",
            ).map { Json.parseToJsonElement(it) }
            WatchNotes("t", "m1", notes, "Claude", null, {})
            QuietCard(padding = 0.dp) {
                StartedRow(summary("1", "Port the drawer to the Fold", running = true), Peek()) {}
                StartedRow(summary("2", "Review the scroll policy", waiting = true), Peek(request = "Which device?")) {}
                StartedRow(summary("3", "Bump Compose"), Peek(ThreadSamples.answer("x", "Bumped, all green."))) {}
            }
        }
    }

    @Test
    fun strips() = part("parts-strips") {
        val task = ChatPending("task", "p1", "Dev server on port 5177", since = ThreadSamples.now - 90_000)
        GoalStrip(ThreadGoal("claude", "Every Android screen wears the new look", "active", tokensUsed = 42_000, updated = ThreadSamples.now), running = true)
        WaitingStrip(listOf(task)) {}
        WaitingStrip(listOf(task, task.copy(id = "p2", description = "Emulator boot"))) {}
        StoppedStrip(listOf(task)) {}
        LimitStrip(LimitResume("m1", "claude", ThreadSamples.now + 3_600_000)) {}
        SettledStrip(false) {}
        SubagentStrip(listOf(agent("a"), agent("b", "completed")), { it }, {}, null, {}, emptyList())
        QueueList(
            listOf(RemoteQueued("q1", "Then run the whole suite and tell me what failed, with the names"), RemoteQueued("q2", "And commit", error = "Couldn't be sent")),
            listOf(RemoteQueued("q3", "Morning summary", at = ThreadSamples.now + 7_200_000)),
            running = true, compacting = false, paused = false, {}, {}, {}, { _, _ -> },
        )
    }

    @Test
    fun progress() = part("parts-progress") {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            ChecklistSteps(ThreadSamples.checklist)
        }
        ProgressStrip(ThreadSamples.checklist) {}
        Box(Modifier.weight(1f).padding(8.dp), contentAlignment = Alignment.BottomCenter) {
            RelaySheetFrame("Progress · 3 of 5", {}) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) { ProgressSteps(ThreadSamples.checklist) }
            }
        }
    }

    @Test
    fun menu() = part("parts-menu") {
        Box(Modifier.weight(1f).padding(8.dp), contentAlignment = Alignment.BottomCenter) {
            RelaySheetFrame("Why does the composer jump?", {}) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    MenuRows(
                        threadMenu(
                            ThreadMenuActions(
                                settled = false, running = false, snoozed = false, branch = "relay/composer-jump", worktree = null, sides = 2,
                                onRename = {}, onTriage = { _, _ -> }, onSnooze = {}, onUnread = {}, onRegenerate = {}, onCopy = {}, onSides = {},
                                onWorktreeChanges = {}, onChanges = {}, onFiles = {}, onHistory = {}, onCompact = {}, onArchive = {},
                                onSettings = {}, settingsLine = "Opus 5.5 · 42% context",
                            ),
                        ),
                        onClose = {},
                    )
                }
            }
        }
    }

    private fun summary(id: String, title: String, running: Boolean = false, waiting: Boolean = false) =
        RemoteChatSummary(id, "relay", title, "project", ThreadSamples.now, ThreadSamples.now, "claude", running = running, waiting = waiting)

    private fun agent(id: String, status: String = "running") =
        SubagentRun(id, description = "Read every screen's padding", status = status, started = ThreadSamples.now - 40_000, summary = "Reading HomeScreen.kt", calls = 12)
}
