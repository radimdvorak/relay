package dev.relay.android.features.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.Spinner
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobile
import io.github.lyxnx.compose.ui.tablericons.outline.Download
import io.github.lyxnx.compose.ui.tablericons.outline.Refresh
import java.text.DateFormat
import java.util.Date

/** ThreadRow's `ago`: how long since `at`, in a couple of words. */
fun ago(at: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = Math.round((now - at) / 60_000.0)
    if (minutes < 1) return "just now"
    if (minutes < 60) return "${minutes}m ago"
    val hours = Math.round(minutes / 60.0)
    if (hours < 24) return "${hours}h ago"
    val days = Math.round(hours / 24.0)
    return when {
        days == 1L -> "yesterday"
        days < 7 -> "${days}d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
    }
}

/** What Settings says about the last look at the feed. */
fun latestStatus(latest: LatestAppState, offer: NewestApp?, installed: String, now: Long = System.currentTimeMillis()): String {
    val newest = latest.newest
    return when {
        latest.checking -> "Looking on GitHub…"
        latest.error != null -> "Couldn't look: ${sentence(latest.error)}"
        newest != null -> {
            val what = when {
                offer != null -> "The newest app is ${newest.version}"
                newest.version == installed -> "This app ($installed) matches the newest APK"
                else -> "The newest APK is ${newest.version}; this app runs $installed"
            }
            val kept = if (newest.release != newest.version) "; Relay ${newest.release} kept it" else ""
            "$what$kept. Looked ${ago(latest.checkedAt ?: 0, now)}."
        }
        else -> "Relay looks on GitHub every few hours."
    }
}

/** The newest app when this one should move to it. */
@Composable
fun appOffer(updates: Updates): NewestApp? {
    val latest by updates.latest.state.collectAsState()
    return updates.latest.offer(latest)
}

@Composable
fun InstalledAppRow(updates: Updates) = ListRow(
    "Relay ${updates.installed}",
    subtitle = "As installed; new versions come from GitHub.",
    leadingIcon = TablerIcons.Outline.DeviceMobile,
    subtitleLines = 3,
)

/** Downloads and installs the newest app on GitHub, whatever the paired computer runs. */
@Composable
fun AppOfferRow(updates: Updates, offer: NewestApp) {
    val apk by updates.installer.state.collectAsState()
    val (label, hint) = offerWords(updates, offer, apk.about(offer.version))
    ListRow(
        label,
        subtitle = hint,
        leadingIcon = TablerIcons.Outline.Download,
        subtitleLines = 4,
        onClick = { updates.install(offer) },
    )
}

private fun offerWords(updates: Updates, offer: NewestApp, mine: ApkInstall?): Pair<String, String?> {
    val version = offer.version
    if (!updates.releaseBuild) return "Download Relay $version" to
        if (mine is ApkInstall.Failed) apkPrompt(version, mine)
        else "A development build: Android won't put the release app over it. This opens the download in the browser."
    return when (mine) {
        is ApkInstall.Downloading if !mine.quiet -> "Downloading Relay $version… ${Math.round(mine.done * 100)}%" to null
        is ApkInstall.Installing -> "Installing Relay $version…" to "It closes when it's done; open it again."
        else -> "Update to Relay $version" to updateHint(updates, offer, mine)
    }
}

private fun updateHint(updates: Updates, offer: NewestApp, mine: ApkInstall?): String = when {
    mine is ApkInstall.Downloaded && updates.installsSilently ->
        "Ready. It installs itself the next time you leave Relay, or now with a tap."
    mine != null && mine !is ApkInstall.Downloading -> apkPrompt(offer.version, mine)
    else -> {
        val size = offer.size?.let { " (${Math.round(it / 1e6)} MB)" } ?: ""
        "Downloads it from GitHub$size and asks Android to install it. Pairings and settings stay."
    }
}

@Composable
fun LookForAppRow(updates: Updates) {
    val latest by updates.latest.state.collectAsState()
    ListRow(
        "Look for a new app",
        subtitle = latestStatus(latest, updates.latest.offer(latest), updates.installed),
        leadingIcon = TablerIcons.Outline.Refresh,
        subtitleLines = 4,
        trailing = if (latest.checking) ({ Spinner() }) else null,
        onClick = { if (!latest.checking) updates.checkNow() },
    )
}

@Composable
fun LatestAppRows(updates: Updates) {
    appOffer(updates)?.let { AppOfferRow(updates, it) }
    LookForAppRow(updates)
}
