package dev.relay.android.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.relay.android.features.home.ActivityList
import dev.relay.android.features.home.DraftFrom
import dev.relay.android.features.home.HomeComposer
import dev.relay.android.features.home.ListedDraft
import dev.relay.android.features.home.StatusNews
import dev.relay.android.features.home.StatusToastHost
import dev.relay.android.features.home.ThreadRow
import dev.relay.android.features.newthread.NewThreadFrame
import dev.relay.android.features.newthread.NewThreadIntro
import dev.relay.android.features.newthread.WhereLine
import dev.relay.android.remote.model.ChatPending
import dev.relay.android.remote.model.LimitResume
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteProject
import dev.relay.android.remote.model.StartedBy
import dev.relay.android.ui.BottomBar
import dev.relay.android.ui.ChromeScreen
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.ToastState
import dev.relay.android.ui.theme.RelayTheme
import org.junit.Test

private const val MINUTE = 60_000L

internal val sampleProjects = listOf(
    RemoteProject("relay", "relay"), RemoteProject("web", "website"), RemoteProject("ps5", "react-on-anything", folder = "Homebrew"),
)

internal fun sampleThread(
    id: String, project: String, title: String, minutesAgo: Long, edit: RemoteChatSummary.() -> RemoteChatSummary = { this },
): RemoteChatSummary {
    val at = System.currentTimeMillis() - minutesAgo * MINUTE
    return RemoteChatSummary(id, project, title, scope = "project", updated = at, created = at - 30 * MINUTE, provider = "claude").edit()
}

private val now get() = System.currentTimeMillis()

internal val busy = listOf(
    sampleThread("2", "relay", "Why does the composer jump on rotate?", 9) { copy(waiting = true, branch = "fix/rotate") },
    sampleThread("1", "relay", "Redesign the Android app", 2) { copy(running = true, runningSince = now - 4 * MINUTE, branch = "android-expressive") },
    sampleThread("k1", "relay", "Rework the thread screen", 2) { copy(running = true, runningSince = now - 3 * MINUTE, startedBy = StartedBy("1", "claude")) },
    sampleThread("k2", "relay", "Rework the lists and the pull request pages", 12) { copy(startedBy = StartedBy("1", "claude"), provider = "codex") },
    sampleThread("6", "ps5", "Find out why the PS5 build of the really long named sample drops every other frame", 1) {
        copy(running = true, runningSince = now - 75 * MINUTE, provider = "codex", branch = "perf/frame-pacing-on-the-second-controller")
    },
    sampleThread("3", "web", "Compare page copy", 40) { copy(branch = "feat/compare") },
    sampleThread("4", "relay", "Bump Compose to 1.13", 180) { copy(markedUnread = true, provider = "codex") },
    sampleThread("7", "relay", "Soak the renderer overnight", 200) { copy(limitResume = LimitResume("m", "claude", now + 90 * MINUTE)) },
    sampleThread("8", "web", "Nightly link check", 600) { copy(nextSend = now + 300 * MINUTE) },
    sampleThread("9", "web", "Draft the changelog", 900),
    sampleThread("s1", "relay", "Look at the crash logs", 1000) { copy(snoozedUntil = now + 800 * MINUTE, snoozedAt = now) },
    sampleThread("5", "web", "Old pricing table", 60 * 26) { copy(settledAt = updated) },
    sampleThread("10", "relay", "Terminal resize from the visible height", 60 * 30) { copy(settledAt = updated) },
)

private val drafts = mapOf(
    "new:web" to ListedDraft("new:web", "Try a warmer hero on the home page, and", DraftFrom.Computer, null, now),
    "9" to ListedDraft("9", "also mention the new themes", DraftFrom.Here, null, now),
)

@Composable
private fun Activity(chats: List<RemoteChatSummary>, drafts: Map<String, ListedDraft> = emptyMap(), filtered: Boolean = false, selected: String? = null) {
    ActivityList(
        chats, sampleProjects, drafts, filtered = filtered,
        unread = { it.markedUnread == true }, folds = emptyMap(), onFold = { _, _ -> },
        bridge = Int.MAX_VALUE, selected = selected, onOpen = {}, onOpenDraft = { _, _ -> },
        onTriage = { _, _ -> null }, bottom = 140.dp, modifier = Modifier.fillMaxSize(),
    )
}

/** The Threads tab as the shell will wear it, with a stand-in header and bar. */
@Composable
internal fun SampleThreadsTab(
    chats: List<RemoteChatSummary> = emptyList(),
    drafts: Map<String, ListedDraft> = emptyMap(),
    filtered: Boolean = false,
    selected: String? = null,
    onOpen: () -> Unit = {},
) {
    var tab by remember { mutableStateOf(SampleTab.Threads) }
    ChromeScreen(header = { SampleHeader() }) {
        Activity(chats, drafts, filtered, selected)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
            HomeComposer(onOpen, Modifier.padding(horizontal = 15.dp))
            BottomBar(sampleTabs, tab, { tab = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }
}

@Composable
internal fun SampleNewThread(recent: Int = 0, scratchError: String? = null, scratch: Boolean = false, composer: @Composable () -> Unit) {
    val project = sampleProjects[0].takeUnless { scratch }
    val picked = if (scratch) "scratch" else "relay"
    NewThreadFrame(onBack = {}, floor = WindowInsets(0)) {
        NewThreadIntro(project, picked, scratchError, busy.take(recent), {}, {}, Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp)) {
            WhereLine(project, picked, "worktree") {}
            composer()
        }
    }
}

@Composable
internal fun WithToasts(fontScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalToasts provides remember { ToastState() },
        LocalDensity provides Density(density.density, density.fontScale * fontScale),
        content = content,
    )
}

@Composable
private fun OnDesktopPalette(content: @Composable () -> Unit) = RelayTheme(desktopDark) {
    WithToasts {
        Box(Modifier.fillMaxSize().background(RelayTheme.colors.surface).padding(bottom = 48.dp)) { content() }
    }
}

/** The Threads tab and the new-thread flow, fed sample threads instead of a computer. */
class HomeShots : ShotTest() {
    private fun everywhere(shot: (ShotDevice, Boolean) -> Unit) {
        for (device in ShotDevice.entries) for (dark in listOf(false, true)) shot(device, dark)
    }

    @Test
    fun activity() = everywhere { device, dark ->
        Shot("home-activity", device, dark) { WithToasts { SampleThreadsTab(busy, drafts, selected = "3") } }
    }

    /** The desktop's dark palette over the phone's 3-button bar. */
    @Test
    fun desktopPalette() {
        Shot("home-activity-desktop", dark = true) { OnDesktopPalette { SampleThreadsTab(busy, drafts) } }
        Shot("home-empty-desktop", dark = true) { OnDesktopPalette { SampleThreadsTab() } }
        Shot("new-thread-desktop", dark = true) {
            OnDesktopPalette { SampleNewThread(recent = 3) { SampleComposer(true, null, Modifier) } }
        }
        Shot("home-toast-desktop", dark = true) {
            OnDesktopPalette {
                SampleThreadsTab(busy, drafts)
                Box(Modifier.fillMaxSize().padding(bottom = 125.dp), contentAlignment = Alignment.BottomEnd) {
                    StatusToastHost(StatusNews("2", "Why does the composer jump on rotate?", StatusNews.Kind.NeedsYou), Modifier.fillMaxWidth(), {}, {})
                }
            }
        }
    }

    @Test
    fun activityLarge() = Shot("home-activity-large-type") { WithToasts(1.3f) { SampleThreadsTab(busy, drafts) } }

    @Test
    fun settledOpen() {
        val quiet = busy.filter { it.id in setOf("3", "s1", "5", "10") }
        Shot("home-settled", act = {
            for (shelf in listOf("Settled", "Snoozed")) compose.onNodeWithText(shelf).performSemanticsAction(SemanticsActions.OnClick)
        }) { WithToasts { SampleThreadsTab(quiet) } }
    }

    @Test
    fun empty() = everywhere { device, dark -> Shot("home-empty", device, dark) { WithToasts { SampleThreadsTab() } } }

    @Test
    fun emptyWithShelves() = Shot("home-empty-shelves") { WithToasts { SampleThreadsTab(busy.takeLast(3)) } }

    @Test
    fun emptyFiltered() = Shot("home-empty-filtered") { WithToasts { SampleThreadsTab(filtered = true) } }

    @Test
    fun threadRows() = Shot("home-thread-rows") {
        Column(Modifier.padding(8.dp)) {
            for (chat in busy.take(6)) ThreadRow(chat, drafts[chat.id], {}, project = "relay", selected = chat.id == "3")
            ThreadRow(busy.first { it.id == "9" }, drafts["9"], {}, project = "website")
            ThreadRow(sampleThread("p", "relay", "Waits on agents", 3) { copy(pending = listOf(ChatPending("task", "t", since = now, agent = true))) }, null, {})
        }
    }

    @Test
    fun newThread() = everywhere { device, dark ->
        Shot("new-thread", device, dark) { WithToasts { SampleNewThread { SampleComposer(true, null, Modifier) } } }
    }

    /** The keyboard takes what it takes; the rest is what's left for the greeting and the latest threads. */
    @Test
    fun newThreadRecent() {
        for (keyboard in listOf(false, true)) Shot("new-thread-recent${if (keyboard) "-keyboard" else ""}") {
            WithToasts {
                Box(Modifier.fillMaxSize().background(Color.Gray).padding(bottom = if (keyboard) 320.dp else 0.dp)) {
                    SampleNewThread(recent = 5) { SampleComposer(true, null, Modifier) }
                }
            }
        }
    }

    @Test
    fun newThreadKeyboard() = Shot("new-thread-keyboard", dark = true) {
        WithToasts(1.3f) {
            Box(Modifier.fillMaxSize().padding(bottom = 320.dp)) { SampleNewThread { SampleComposer(true, null, Modifier) } }
        }
    }

    @Test
    fun scratchFailed() = Shot("new-thread-scratch-failed") {
        WithToasts { SampleNewThread(scratch = true, scratchError = "Not connected to your computer.") { SampleComposer(true, null, Modifier) } }
    }
}
