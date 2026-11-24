package dev.relay.android.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.relay.android.app.Crash
import dev.relay.android.app.CrashScreen
import dev.relay.android.app.gallery.GalleryScreen
import dev.relay.android.features.applock.PadKey
import dev.relay.android.features.applock.PinPad
import dev.relay.android.features.files.FileContents
import dev.relay.android.features.files.FileEntries
import dev.relay.android.features.files.fileEntries
import dev.relay.android.features.notes.NoteCard
import dev.relay.android.features.tasks.TaskList
import dev.relay.android.features.tunnel.BrowserBar
import dev.relay.android.features.tunnel.BrowserBarState
import dev.relay.android.features.tunnel.BrowserFrame
import dev.relay.android.remote.model.LocalFile
import dev.relay.android.remote.model.ProjectTask
import dev.relay.android.remote.model.ThreadNote
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.EmptyState
import dev.relay.android.ui.Page
import dev.relay.android.ui.RelaySheetFrame
import dev.relay.android.ui.SearchField
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import dev.relay.android.ui.theme.ThemeMode
import dev.relay.android.ui.theme.resolveColors
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Fingerprint
import io.github.lyxnx.compose.ui.tablericons.outline.X
import org.junit.Test

/** The smaller screens: files, tasks, notes, the lock, the browser's chrome, the crash page, the gallery. */
class ScreensShots : ShotTest() {
    private val palettes = listOf(false, true)
    /** The desktop's dark palette, which most phones wear. */
    @Composable
    private fun Desktop(content: @Composable () -> Unit) =
        RelayTheme(resolveColors(ThemeMode.Computer, SampleDesktop, systemDark = true), content)

    @Test
    fun desktop() {
        Shot("files-desktop", dark = true, act = ::systemBars) { Desktop { Files() } }
        Shot("lock-desktop", dark = true, act = ::systemBars) { Desktop { Lock() } }
        Shot("tasks-desktop", dark = true) {
            Desktop { SampleShell { Page("Tasks", onBack = {}) { TaskList(tasks, emptyMap(), {}, {}, {}) } } }
        }
    }

    private val files = listOf(
        "android/app/build.gradle.kts", "android/README.md", "docs/a.md", "src/main.ts", "package.json", "README.md",
        "a-file-with-a-name-long-enough-to-run-off-the-edge-of-any-phone.config.json",
    )

    @Composable
    private fun Files(query: String = "", list: List<String> = files) = SampleShell {
        Page("relay-android-expressive", onBack = {}) {
            SearchField(query, {}, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), placeholder = "Search files…")
            FileEntries(fileEntries(list, "", query), searching = query.isNotBlank()) {}
        }
    }

    @Test
    fun files() {
        for (device in ShotDevice.entries) for (dark in palettes) Shot("files", device, dark) { Files() }
        Shot("files-search") { Files("md") }
        Shot("files-none") { Files("zzz") }
    }

    @Test
    fun file() {
        val code = javaClass.getResource("/splash-guide.json")!!.readText().take(1200)
        for (dark in palettes) Shot("file", dark = dark) {
            SampleShell {
                Page("SettingsScreen.kt", onBack = {}) {
                    Text("android/app/src/main/SettingsScreen.kt  ·  ax/screens", Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp), style = RelayTheme.type.codeSmall, color = RelayTheme.colors.inkSoft)
                    FileContents(LocalFile("a.kt", contents = "import kotlin.math.max\n\n/** A sample. */\nfun biggest(list: List<Int>): Int =\n    list.fold(0) { a, b -> max(a, b) }\n\n$code", branch = "ax/screens"), "a.kt")
                }
            }
        }
    }

    private fun task(id: String, title: String, command: String, ports: List<Int>, worktree: String? = null) = ProjectTask(
        id = id, title = title, command = command, started = System.currentTimeMillis() - 5 * 60_000,
        origin = "relay", ports = ports, worktree = worktree,
    )

    private val tasks = listOf(
        task("1", "Vite dev server", "npm run dev -- --host 127.0.0.1 --port 5177", listOf(5177)),
        task("2", "Gradle", "./gradlew :app:assembleDebug --continuous --configuration-cache --build-cache -Pshots -Pexperimental=true --info --stacktrace", listOf(8080, 9229, 35729), worktree = "ax"),
    )

    @Test
    fun tasks() {
        for (device in ShotDevice.entries) for (dark in palettes) Shot("tasks", device, dark) {
            SampleShell { Page("Tasks", onBack = {}) { TaskList(tasks, mapOf("2" to "stop"), {}, {}, {}) } }
        }
        Shot("tasks-none") { SampleShell { Page("Tasks", onBack = {}) { TaskList(emptyList(), emptyMap(), {}, {}, {}) } } }
    }

    @Test
    fun notes() {
        val notes = listOf(
            ThreadNote(id = "1", text = "Before merging:\n\n1. Render pairing at the base\n2. Compare with **now**\n3. Remove the worktree", done = listOf(0), by = "claude", created = 1, updated = 1),
            ThreadNote(id = "2", text = "The lock must cover content with no flash on resume. See `LockWindow`.", created = 1, updated = 1),
            ThreadNote(id = "3", text = "Which palette does the pairing screen wear?", ref = true, from = "m", created = 1, updated = 1),
        )
        for (dark in palettes) Shot("notes", dark = dark) {
            SampleShell {
                Column(Modifier.fillMaxSize().background(RelayTheme.colors.chrome).padding(8.dp), verticalArrangement = Arrangement.Bottom) {
                    RelaySheetFrame("Notes", onClose = {}) {
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            notes.forEach { NoteCard(it, {}, {}, { _, _ -> }, {}) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Lock(typed: Int = 3, error: Boolean = false) = PinPad(
        title = "Relay is locked",
        line = if (error) "Wrong PIN. One more and Relay makes you wait." else "Enter your PIN",
        typed = typed, onDigit = {}, onDelete = {}, error = error, warn = error,
        extra = PadKey(TablerIcons.Outline.Fingerprint, "Fingerprint") {},
        footer = { Button("Forgot PIN?", {}, variant = ButtonVariant.Ghost, size = ButtonSize.Sm) },
    )

    @Test
    fun lock() {
        for (device in ShotDevice.entries) for (dark in palettes) Shot("lock", device, dark) { Lock() }
        Shot("lock-wrong", dark = true) { Lock(6, error = true) }
        Shot("lock-flow") {
            PinPad("Choose a PIN", "Six digits, asked for whenever Relay opens.", 0, {}, {}, top = { CircleButton(TablerIcons.Outline.X, "Cancel", {}, Modifier.padding(16.dp)) })
        }
        Shot("lock-big") {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) { Lock(error = true) }
        }
    }

    @Test
    fun keyStrip() {
        Strip("strip-key", frames = 6, start = { compose.onNodeWithContentDescription("5").performTouchInput { down(center) } }) { Lock() }
        compose.onNodeWithContentDescription("5").performTouchInput { up() }
        Strip("strip-key-tap", frames = 10, start = { compose.onNodeWithContentDescription("5").performTouchInput { down(center); up() } }) { Lock() }
    }

    @Test
    fun browser() {
        for (device in ShotDevice.entries) for (dark in palettes) Shot("browser", device, dark) {
            SampleShell {
                BrowserFrame(header = {
                    BrowserBar(BrowserBarState("localhost:5177/previews/website/", showsPage = true, canBack = true, canForward = false), {}, {}, {}, {}, {}, {}, {})
                }) {
                    EmptyState("Couldn't load localhost:5177", description = "Not connected to Studio.", action = { Button("Try again", {}, variant = ButtonVariant.Secondary, size = ButtonSize.Sm) })
                }
            }
        }
        Shot("browser-edit") {
            SampleShell {
                BrowserFrame(header = { BrowserBar(BrowserBarState("", showsPage = false, canBack = false, canForward = false), {}, {}, {}, {}, {}, {}, {}) }) {}
            }
        }
    }

    @Test
    fun crash() {
        for (dark in palettes) Shot("crash", dark = dark) {
            CrashScreen(Crash("IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints", "details"), {})
        }
    }

    @Test
    fun gallery() {
        for (dark in palettes) for (page in 0..4) Shot(
            "gallery-$page", dark = dark,
            act = {
                repeat(page) {
                    compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp(durationMillis = 400) }
                    compose.mainClock.advanceTimeBy(1_500)
                }
            },
        ) { SampleShell { GalleryScreen() } }
    }
}
