package dev.relay.android.shots

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.relay.android.features.markdown.Markdown
import dev.relay.android.features.thread.AgentRun
import dev.relay.android.features.thread.ThreadFloating
import dev.relay.android.features.thread.ThreadScroll
import dev.relay.android.features.thread.fadesThreadEnd
import dev.relay.android.features.thread.rememberThreadScroll
import dev.relay.android.features.thread.scroll.ContentSignature
import dev.relay.android.features.thread.scroll.ThreadViewport
import dev.relay.android.ui.theme.RelayPaperLight
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import java.io.File

/** Frame by frame: what moves in a thread, for looking at jumps and clipping. */
class ThreadMotionShots : ShotTest() {
    private fun frames(name: String, count: Int, stepMs: Long = 33) {
        for (i in 0 until count) {
            val file = File(System.getProperty("relay.shots.dir") ?: "build/shots", "$name-${i.toString().padStart(2, '0')}.png")
            compose.onRoot().captureRoboImage(file.path)
            compose.mainClock.advanceTimeBy(stepMs)
        }
    }

    /** Scrolled up: the way back appears; tapped, it's the end again. */
    @Test
    fun jump() {
        RuntimeEnvironment.setQualifiers("w412dp-h500dp-xhdpi")
        compose.mainClock.autoAdvance = false
        lateinit var scroll: ThreadScroll
        compose.activity.setContent {
            RelayTheme(RelayPaperLight) {
                scroll = rememberThreadScroll()
                val keys = (0 until 30).map { "row-$it" } + "end"
                ThreadViewport(
                    scroll, keys, ContentSignature(keys.size, "end", 0, 0), Modifier.fillMaxSize().background(RelayTheme.colors.surface),
                    dockModifier = Modifier.fadesThreadEnd(),
                    dock = { Box(Modifier.fillMaxWidth().padding(15.dp).height(53.dp).background(RelayTheme.colors.container, RelayTheme.shapes.circle)) },
                    overDock = { ThreadFloating(scroll, null) },
                ) {
                    items(30, key = { "row-$it" }) { Box(Modifier.padding(horizontal = 18.dp, vertical = 7.dp)) { Markdown("Paragraph $it, with enough words in it to wrap once on a phone this wide.", native = true) } }
                    item("end") { Box(Modifier.height(10.dp)) }
                }
            }
        }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(300)
        compose.runOnUiThread {
            scroll.readerScroll.onPreScroll(Offset(0f, 6f), NestedScrollSource.UserInput)
            scroll.list.requestScrollToItem(10, 12)
            runBlocking { scroll.readerScroll.onPostFling(Velocity.Zero, Velocity.Zero) }
        }
        frames("motion-jump", 8)
        compose.runOnUiThread { scroll.toBottom("jump_to_latest") }
        frames("motion-jump-back", 6)
    }

    /** A run the agent moves on from folds into its summary. */
    @Test
    fun runCloses() {
        RuntimeEnvironment.setQualifiers("w412dp-h500dp-xhdpi")
        compose.mainClock.autoAdvance = false
        val trace = mutableStateOf(ThreadSamples.trace.take(4))
        compose.activity.setContent {
            RelayTheme(RelayPaperLight) {
                Column(Modifier.fillMaxSize().background(RelayTheme.colors.surface).padding(18.dp)) {
                    AgentRun("t", ThreadSamples.answer("m", "", status = "streaming", trace = trace.value), null)
                }
            }
        }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(300)
        trace.value = ThreadSamples.trace.take(5)
        Snapshot.sendApplyNotifications()
        frames("motion-run", 12)
    }
}
