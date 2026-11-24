package dev.relay.android.shots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.relay.android.features.home.ProjectsList
import dev.relay.android.features.project.ProjectFacts
import dev.relay.android.features.project.ProjectPage
import dev.relay.android.remote.model.LineCounts
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteOverview
import dev.relay.android.remote.model.RemoteProject
import dev.relay.android.remote.model.RemoteProjectComputer
import dev.relay.android.remote.model.WorkingChange
import dev.relay.android.remote.model.WorkingTree
import dev.relay.android.ui.ChromeHeader
import dev.relay.android.ui.ChromeScreen
import dev.relay.android.ui.CircleAction
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CirclePair
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.HeaderTitle
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.ToastState
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Filter
import io.github.lyxnx.compose.ui.tablericons.outline.Menu2
import io.github.lyxnx.compose.ui.tablericons.outline.Search
import org.junit.Test

/** A tab's list under the header the shell gives it. */
@Composable
internal fun SampleTabPage(title: String, content: @Composable () -> Unit) {
    ChromeScreen(header = {
        ChromeHeader(
            title = title,
            titleStyle = HeaderTitle.Large,
            leading = { CircleButton(TablerIcons.Outline.Menu2, "Menu", {}, tone = CircleTone.Raised) },
            trailing = {
                CirclePair(CircleAction(TablerIcons.Outline.Search, "Search", {}), CircleAction(TablerIcons.Outline.Filter, "Filter", {}))
            },
        )
    }) { Box(androidx.compose.ui.Modifier.fillMaxSize()) { content() } }
}

/** Text as large as the phone's accessibility settings make it. */
@Composable
internal fun LargeText(scale: Float = 1.3f, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale), content = content)
}

internal fun sampleThread(
    id: String, project: String, title: String, minutesAgo: Long,
    running: Boolean = false, waiting: Boolean = false, branch: String? = null,
): RemoteChatSummary {
    val at = System.currentTimeMillis() - minutesAgo * 60_000L
    return RemoteChatSummary(
        id, project, title, scope = "project", updated = at, created = at - 1_800_000, provider = "claude",
        running = running, waiting = waiting, branch = branch, runningSince = at.takeIf { running },
    )
}

/** Tells the window it has Android's 48dp three-button bar, which Robolectric leaves out. */
internal fun ShotTest.threeButtonBar() {
    val bar = (48 * compose.activity.resources.displayMetrics.density).toInt()
    compose.runOnUiThread {
        val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bar)).build()
        ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
    }
}

/** The Projects tab and a project's own page. */
class ProjectShots : ShotTest() {
    private val projects = listOf(
        RemoteProject("scratch1", "scratch", scratch = true),
        RemoteProject("relay", "relay"),
        RemoteProject("web", "website"),
        RemoteProject("long", "a-monorepo-with-a-really-long-name-that-keeps-on-going-forever"),
        RemoteProject("plain", "notes", plain = true),
        RemoteProject("anyfin", "anyfin", folder = "Homebrew"),
        RemoteProject("tt", "tt-decomp", folder = "Homebrew", computer = RemoteProjectComputer("thinkcenter")),
        RemoteProject("old", "old-laptop-project", folder = "Homebrew", computer = RemoteProjectComputer("winlap", gone = true)),
    )
    private val chats = listOf(
        sampleThread("s1", "scratch1", "How do floating toolbars work?", 20),
        sampleThread("1", "relay", "Redesign the Android app", 2, running = true, branch = "android-expressive"),
        sampleThread("2", "relay", "Why does the composer jump on rotate?", 9, waiting = true),
        sampleThread("3", "web", "Compare page copy", 40),
        sampleThread("4", "anyfin", "Subtitles drift", 60 * 30),
        sampleThread("5", "tt", "Port the replay camera", 60 * 24 * 9, running = true),
        sampleThread("6", "old", "Leftover", 60 * 24 * 40),
    )

    @Composable
    private fun Projects(overview: RemoteOverview) {
        CompositionLocalProvider(LocalToasts provides ToastState()) {
            SampleTabPage("Projects") {
                ProjectsList(overview, emptyMap(), selected = null, {}, {}, {}, bottom = 96.dp)
            }
        }
    }

    @Test
    fun list() {
        for (device in ShotDevice.entries) for (dark in listOf(false, true))
            Shot("projects", device, dark) { Projects(RemoteOverview(name = "mac", projects = projects, chats = chats)) }
        Shot("projects-relay-dark") { InPalette("relay-dark") { Projects(RemoteOverview(name = "mac", projects = projects, chats = chats)) } }
        Shot("projects-large") { LargeText { Projects(RemoteOverview(name = "mac", projects = projects, chats = chats)) } }
        Shot("projects-empty") { Projects(RemoteOverview(name = "mac", projects = emptyList(), chats = emptyList())) }
        val many = (1..300).map { RemoteProject("p$it", "project-$it") }
        Shot("projects-many") { Projects(RemoteOverview(name = "mac", projects = many, chats = many.map { sampleThread("c${it.id}", it.id, "t", 5) })) }
    }

    private val tree = WorkingTree(
        head = "abc", branch = "android-expressive", revision = "r",
        changes = List(12) { WorkingChange("a$it.kt", index = "M", worktree = " ") },
        ahead = 3, behind = 1, lines = LineCounts(846, 231),
    )

    private fun facts(tree: WorkingTree? = this.tree, name: String = "relay") = ProjectFacts(
        "relay", name, null, git = true, tree = tree, tasks = 2, ports = listOf(5177), tunnels = true, host = "mac",
    )

    @Composable
    private fun Project(facts: ProjectFacts, chats: List<RemoteChatSummary>) {
        CompositionLocalProvider(LocalToasts provides ToastState()) {
            ProjectPage(facts, chats, emptyMap(), refreshing = false, onRefresh = {}, onBack = {}, open = {})
        }
    }

    @Test
    fun page() {
        val threads = chats.filter { it.projectId == "relay" } + List(8) { sampleThread("x$it", "relay", "An older thread number $it", 60L * (it + 3)) }
        for (device in ShotDevice.entries) for (dark in listOf(false, true)) Shot("project", device, dark) { Project(facts(), threads) }
        Shot("project-relay-dark", act = { threeButtonBar() }) { InPalette("relay-dark") { Project(facts(), threads) } }
        val messy = tree.copy(branch = "feature/lists-rework/with-a-really-long-branch-name-from-a-ticket-1234", operation = "MERGE_HEAD")
        Shot("project-long") { LargeText { Project(facts(messy, "a-monorepo-with-a-really-long-name-that-keeps-on-going"), threads) } }
        Shot("project-empty") {
            Project(facts(null).copy(git = false, tasks = null, tunnels = true, computer = RemoteProjectComputer("thinkcenter"), ports = listOf(3000)), emptyList())
        }
    }
}
