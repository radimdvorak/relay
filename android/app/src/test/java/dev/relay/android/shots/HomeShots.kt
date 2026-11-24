package dev.relay.android.shots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import dev.relay.android.features.home.ActivityList
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteProject
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.ToastState
import org.junit.Test

/** Home's Activity list, fed sample threads instead of a computer. */
class HomeShots : ShotTest() {
    private val minute = 60_000L
    private val now = System.currentTimeMillis()

    private val projects = listOf(RemoteProject("relay", "relay"), RemoteProject("web", "website"))

    private val chats = listOf(
        thread("1", "relay", "Redesign the Android app", 2, running = true),
        thread("2", "relay", "Why does the composer jump on rotate?", 9, waiting = true),
        thread("3", "web", "Compare page copy", 40),
        thread("4", "relay", "Bump Compose to 1.13", 180, unread = true),
        thread("5", "web", "Old pricing table", 60 * 26, settled = true),
    )

    private fun thread(
        id: String, project: String, title: String, minutesAgo: Long,
        running: Boolean = false, waiting: Boolean = false, unread: Boolean = false, settled: Boolean = false,
    ): RemoteChatSummary {
        val at = now - minutesAgo * minute
        return RemoteChatSummary(
            id, project, title, scope = "project", updated = at, created = at - 30 * minute, provider = "claude",
            running = running, waiting = waiting, markedUnread = unread, settledAt = if (settled) at else null,
        )
    }

    @Test
    fun activity() {
        for (device in ShotDevice.entries) for (dark in listOf(false, true)) Shot("home-activity", device, dark) {
            CompositionLocalProvider(LocalToasts provides ToastState()) {
                ActivityList(
                    chats, projects, drafts = emptyMap(), filtered = false,
                    unread = { it.markedUnread == true }, folds = emptyMap(), onFold = { _, _ -> },
                    bridge = Int.MAX_VALUE, selected = null, onOpen = {}, onOpenDraft = { _, _ -> },
                    onTriage = { _, _ -> null }, bottom = 0.dp,
                )
            }
        }
    }
}
