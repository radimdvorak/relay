package dev.relay.android.features.git

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.projectCiStatus
import dev.relay.android.remote.model.CiRun
import dev.relay.android.remote.model.CiStatus
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.CircleSurface
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.Group
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.CircleCheck
import io.github.lyxnx.compose.ui.tablericons.outline.CircleDashed
import io.github.lyxnx.compose.ui.tablericons.outline.CircleX
import io.github.lyxnx.compose.ui.tablericons.outline.ExternalLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private val ciLabels = mapOf("success" to "CI passed", "failure" to "CI failed", "running" to "CI running", "skipped" to "CI skipped")

fun ciLabel(state: String) = ciLabels[state] ?: "CI $state"

/** CI's overall mark: still, not spinning, so a header or a list of them stays calm while an agent works. */
@Composable
fun CiMark(state: String, size: Dp = 16.dp, contentDescription: String? = ciLabel(state)) {
    val c = RelayTheme.colors
    when (state) {
        "success" -> Icon(TablerIcons.Outline.CircleCheck, contentDescription, tint = c.success, size = size)
        "failure" -> Icon(TablerIcons.Outline.CircleX, contentDescription, tint = c.danger, size = size)
        "running" -> Icon(TablerIcons.Outline.CircleDashed, contentDescription, tint = c.waiting, size = size)
        else -> Icon(TablerIcons.Outline.CircleDashed, contentDescription, tint = c.inkFaint, size = size)
    }
}

/**
 * CI on the thread's branch, as the desktop's thread header shows it: one
 * mark for the overall state, and the runs behind it a tap away
 * (mobile/src/ui/CiStatus.tsx). Shows nothing until a run has a verdict.
 * [running] is whether the agent works: it may push, so its end looks again.
 */
@Composable
fun CiStatusButton(remote: Remote, projectId: String, chatId: String, running: Boolean = false) {
    var ci by remember { mutableStateOf<CiStatus?>(null) }
    var open by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val live = rememberLive(remote)
    suspend fun load() {
        ci = try {
            remote.call { projectCiStatus(projectId, chatId) }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            null
        }
    }
    // Now and every minute while on screen and the app in front.
    LaunchedEffect(live, projectId, chatId) {
        while (live) {
            now = System.currentTimeMillis()
            load()
            delay(60_000)
        }
    }
    var wasRunning by remember { mutableStateOf(running) }
    LaunchedEffect(running) {
        if (wasRunning && !running && live) load()
        wasRunning = running
    }

    val status = ci ?: return
    val summary = ciSummary(status.runs) ?: return
    val uri = LocalUriHandler.current
    CircleSurface(
        { open = true },
        "${ciLabel(summary.state)} on ${status.branch}",
        Modifier.size(CircleSize.Header.diameter),
        RelayTheme.shapes.circle,
        CircleTone.Raised,
        enabled = true,
        haptic = null,
    ) { CiMark(summary.state, CircleSize.Header.icon, contentDescription = null) }
    if (open) Sheet({ open = false }, title = "CI", description = "On ${status.branch}") {
        CiRuns(status, now) { runCatching { uri.openUri(it.url) } }
    }
}

/** The commit CI looked at and each of its runs; a tap opens the run on the host. */
@Suppress("UnusedReceiverParameter")
@Composable
internal fun ColumnScope.CiRuns(status: CiStatus, now: Long, onOpen: (CiRun) -> Unit) {
    val c = RelayTheme.colors
    status.commit.message?.takeIf { it.isNotEmpty() }?.let {
        Text("${status.commit.sha.take(7)} · ${it.lineSequence().first()}", style = RelayTheme.type.meta, color = c.inkSoft, maxLines = 2)
    }
    if (status.ahead > 0) Text(
        (if (status.ahead == 1) "1 newer commit hasn’t" else "${status.ahead} newer commits haven’t") + " run yet.",
        style = RelayTheme.type.meta,
        color = c.inkSoft,
    )
    Group {
        for (run in status.runs) item {
            ListRow(
                run.workflow,
                subtitle = (run.failedJob?.let { "$it failed · " } ?: "") + shortAge(run.at, now),
                subtitleLines = 1,
                leading = { CiMark(run.state, 20.dp) },
                trailing = { Icon(TablerIcons.Outline.ExternalLink, "Open ${run.workflow}", tint = c.inkFaint, size = 16.dp) },
                onClick = { onOpen(run) },
            )
        }
    }
}
