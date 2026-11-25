package dev.relay.android.shots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.relay.android.features.home.StatusNews
import dev.relay.android.features.home.StatusToastHost
import dev.relay.android.features.thread.ThreadCache
import dev.relay.android.features.thread.ThreadScreen
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.Navigator
import dev.relay.android.lib.navigation.Route
import dev.relay.android.remote.Bridge
import dev.relay.android.remote.OfflineCache
import dev.relay.android.remote.Remote
import dev.relay.android.remote.credentials.ComputerSeen
import dev.relay.android.remote.credentials.CredentialStore
import dev.relay.android.remote.credentials.SecretBox
import dev.relay.android.remote.model.ChatMessage
import dev.relay.android.remote.model.ChatScope
import dev.relay.android.remote.model.ChatWorktree
import dev.relay.android.remote.model.ModelChoice
import dev.relay.android.remote.model.RemoteChat
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteOverview
import dev.relay.android.remote.model.RemoteProject
import dev.relay.android.remote.model.ResumeSettings
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.ToastEdge
import dev.relay.android.ui.ToastHost
import dev.relay.android.ui.ToastState
import dev.relay.android.ui.Tone
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The real thread screen over a saved copy, as it opens while its computer is away. */
class ThreadShots : ShotTest() {
    @get:Rule val folder = TemporaryFolder()

    private val both = listOf(false, true)
    private val conversation = listOf(
        ThreadSamples.user("u1", "Why does the composer jump on rotate?"),
        ThreadSamples.answer("m1", "The inset was read twice, so a rotation applied it twice for one frame. It's read once now, in the screen.", trace = ThreadSamples.trace, changes = ThreadSamples.changes),
    )

    @Test
    fun dinner() = screen("thread-dinner", listOf(
        ThreadSamples.user("u1", "Easy cozy dinner for friends"),
        ThreadSamples.answer("m1", ThreadSamples.MENU),
    ))

    @Test
    fun work() = screen("thread-work", conversation)

    @Test
    fun markdown() = screen("thread-markdown", listOf(ThreadSamples.user("u1", "Show me everything"), ThreadSamples.answer("m1", ThreadSamples.MARKDOWN)))

    @Test
    fun tables() = screen("thread-tables", listOf(ThreadSamples.user("u1", "Compare them"), ThreadSamples.answer("m1", ThreadSamples.TABLES)))

    @Test
    fun streaming() = screen("thread-streaming", listOf(
        ThreadSamples.user("u1", "A longer message, to see how several lines of it wrap inside the bubble on the right, with a file: `/tmp/pastes/0a1b2c3d-0000-4000-8000-000000000000/report.pdf`"),
        ThreadSamples.answer("m1", "", status = "streaming", minutesAgo = 1, trace = ThreadSamples.liveTrace),
        ThreadSamples.user("u2", "And check the Fold too", minutesAgo = 0, unread = true),
    ), running = true)

    @Test
    fun approval() = screen("thread-approval", conversation.take(1) + ThreadSamples.answer("m1", "", status = "streaming", minutesAgo = 1, trace = ThreadSamples.liveTrace), running = true) {
        it.copy(requests = listOf(ThreadSamples.approval))
    }

    @Test
    fun question() = screen("thread-question", conversation.take(1), running = true) { it.copy(requests = listOf(ThreadSamples.question, ThreadSamples.approval)) }

    @Test
    fun asks() = screen("thread-asks", listOf(
        ThreadSamples.user("u1", "Publish it", side = true),
        ThreadSamples.answer("m1", "Two things first.", questions = listOf(ThreadSamples.asked, ThreadSamples.secret)),
    ))

    @Test
    fun failed() = screen("thread-failed", conversation.take(1) + listOf(
        ThreadSamples.answer("m1", "Got this far.", status = "failed", error = "API Error: 529 {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}", trace = ThreadSamples.trace),
        ChatMessage("k1", "assistant", "", "complete", ThreadSamples.now, "claude", 1, compaction = true, compactSummary = "We were fixing the inset.", compactedFrom = 182_000.0),
    ))

    @Test
    fun large() = Shot("thread-large") {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) { Thread("thread-large", conversation, false) { it } }
    }

    /** What the app says over an open thread: at its top, with something to undo at the bottom, clear of the dock. */
    @Test
    fun toasts() {
        val waiting: (RemoteChat) -> RemoteChat = { it.copy(requests = listOf(ThreadSamples.approval)) }
        Shot("thread-toast-error", dark = true, act = ::dockMeasured) { Toasted({ show("Couldn't stop it", "Stop may still go through", tone = Tone.Destructive) }, null) { it } }
        Shot("thread-toast-news", dark = true, act = ::dockMeasured) { Toasted({}, needsYou) { it } }
        Shot("thread-toast-both", dark = true, act = ::dockMeasured) { Toasted({ show("Couldn't stop it", tone = Tone.Destructive) }, needsYou) { it } }
        Shot("thread-toast-approval", dark = true, act = ::dockMeasured) { Toasted({ undo("Settled") {} }, needsYou, waiting) }
    }

    private val needsYou = StatusNews("2", "Why is the sidebar empty?", StatusNews.Kind.NeedsYou)

    /** The thread loads off the main thread, and nothing but the test says its dock was measured once it's in. */
    private fun dockMeasured() {
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        Snapshot.sendApplyNotifications()
    }

    @Composable
    private fun Toasted(say: ToastState.() -> Unit, news: StatusNews?, edit: (RemoteChat) -> RemoteChat) = RelayTheme(desktopDark) {
        val toasts = remember { ToastState().apply { plainEdge = ToastEdge.Top }.apply(say) }
        CompositionLocalProvider(LocalToasts provides toasts) {
            Thread("thread-toasts", conversation, running = false, toasts, edit)
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.TopCenter).padding(top = 76.dp)) {
                    StatusToastHost(news, Modifier, {}, {})
                    ToastHost(toasts, ToastEdge.Top)
                }
                ToastHost(toasts, ToastEdge.Bottom, Modifier.align(Alignment.BottomEnd))
            }
        }
    }

    private fun screen(name: String, messages: List<ChatMessage>, running: Boolean = false, edit: (RemoteChat) -> RemoteChat = { it }) {
        for (dark in both) Shot(name, dark = dark) { Thread(name, messages, running, edit) }
        Shot(name, ShotDevice.FoldInner, dark = false) { Thread(name, messages, running, edit) }
        Shot("$name-desktop", dark = true) { RelayTheme(desktopDark) { Thread(name, messages, running, edit) } }
    }

    @Composable
    private fun Thread(id: String, messages: List<ChatMessage>, running: Boolean, edit: (RemoteChat) -> RemoteChat) =
        Thread(id, messages, running, remember { ToastState() }, edit)

    @Composable
    private fun Thread(id: String, messages: List<ChatMessage>, running: Boolean, toasts: ToastState, edit: (RemoteChat) -> RemoteChat) {
        val context = LocalContext.current
        val remote = remember {
            val chat = edit(
                RemoteChat(
                    id, "relay", "Why does the composer jump on rotate?", messages, scope = ChatScope("project"), running = running,
                    root = "/work/relay", worktree = ChatWorktree("/work/relay-composer", "relay/composer-jump"),
                    settings = ResumeSettings("claude", ModelChoice("claude-opus-5-5"), runtimeMode = "full-access", interactionMode = "default"),
                ),
            )
            ThreadCache.of(context).save("pc", id, chat)
            sampleRemote(folder, chat)
        }
        CompositionLocalProvider(LocalNavigator provides NoNavigator, LocalToasts provides toasts) {
            Box(Modifier.fillMaxSize()) { ThreadScreen(id, remote) }
        }
    }
}

object NoNavigator : Navigator {
    override val stack = listOf<Route>(Route.Home)
    override val canGoBack = true
    override fun push(route: Route) = Unit
    override fun pop() = false
    override fun replace(route: Route) = Unit
    override fun openInPane(route: Route) = Unit
    override fun home() = Unit
}

/** A remote with nothing behind it that already knows one computer's overview. */
fun sampleRemote(folder: TemporaryFolder, chat: RemoteChat): Remote {
    val remote = Remote(
        CredentialStore(folder.newFolder(), object : SecretBox {
            override fun seal(plain: ByteArray) = plain
            override fun open(sealed: ByteArray) = sealed
        }),
        OfflineCache(folder.newFolder()), ComputerSeen(folder.newFile()), "1.0.0", deviceName = "Sample phone", warn = { _, _ -> },
    )
    remote.hold("_active", "pc")
    remote.hold("_overview", RemoteOverview(
        "studio", bridge = Bridge.VERSION, readAloud = true, projects = listOf(RemoteProject("relay", "relay")),
        chats = listOf(
            RemoteChatSummary(chat.id, "relay", chat.title, "project", ThreadSamples.now, ThreadSamples.now - 900_000, "claude", running = chat.running, branch = "relay/composer-jump"),
        ),
    ))
    return remote
}

private fun Remote.hold(name: String, value: Any) {
    val field = Remote::class.java.getDeclaredField(name).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    (field.get(this) as MutableStateFlow<Any?>).value = value
}
