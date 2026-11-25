package dev.relay.android.shots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.relay.android.features.git.ChangesPage
import dev.relay.android.features.git.ChangesState
import dev.relay.android.remote.model.WorkingChange
import dev.relay.android.remote.model.WorkingTree
import org.junit.Test
import java.io.File

/** The toolbar arriving once the changes have loaded, a frame every ~33ms. */
class GitMotionShots : ShotTest() {
    @Test
    fun toolbarArrives() {
        var state by mutableStateOf(ChangesState())
        Shot("toolbar-arrives", act = {
            val tree = WorkingTree(branch = "dev", revision = "r", changes = List(4) { WorkingChange("a/File$it.kt", index = " ", worktree = "M") }, ahead = 2, pushTarget = "origin/dev")
            compose.mainClock.advanceTimeBy(500)
            frame("before")
            compose.runOnUiThread { state = ChangesState(tree) }
            Snapshot.sendApplyNotifications()
            for (i in 0 until 12) {
                compose.mainClock.advanceTimeBy(33, ignoreFrameDuration = true)
                frame("%02d".format(i))
            }
        }) { ChangesPage(state, {}, {}, {}, { _, _ -> }, {}, {}, {}) }
    }

    private fun frame(name: String) =
        compose.onRoot().captureRoboImage(File(System.getProperty("relay.shots.dir") ?: "build/shots", "strip-toolbar-$name.png").path)
}
