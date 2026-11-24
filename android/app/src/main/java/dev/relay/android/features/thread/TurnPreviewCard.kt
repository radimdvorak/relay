package dev.relay.android.features.thread

// The desktop's TurnPreview (src/features/browser/TurnPreview.tsx): the page a
// turn's agent left the thread's preview on, under its answer: its address over
// the frame it kept, either of which opens the page. The
// phone can't reach the computer's own addresses, so a local page is only text.

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.relay.android.features.images.ImageSource
import dev.relay.android.features.images.rememberImage
import dev.relay.android.remote.Bridge
import dev.relay.android.remote.model.TurnPreview
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.features.tunnel.rememberTunnels
import dev.relay.android.remote.tunnel.LocalUrls
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ExternalLink
import io.github.lyxnx.compose.ui.tablericons.outline.World
import java.net.URI
import androidx.compose.foundation.layout.heightIn

// Fixed, so loading the frame doesn't move the thread; cropped from the top, as on the desktop.
private val FRAME_HEIGHT = 200.dp
private val LOCAL_HOSTS = setOf("localhost", "0.0.0.0", "::1")

internal fun shortUrl(url: String) = url.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")

/** A web page the phone can open itself: not one on the computer's own addresses. */
internal fun opensOnPhone(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
    val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return false
    return host !in LOCAL_HOSTS && !host.endsWith(".localhost") && !host.startsWith("127.")
}

@Composable
fun TurnPreviewCard(chatId: String, messageId: String, preview: TurnPreview) {
    val remote = LocalThreadRemote.current ?: return
    val bridge = remote.overview.collectAsState().value?.bridge ?: 1
    if (bridge < Bridge.PREVIEW) return
    val c = RelayTheme.colors
    val uri = LocalUriHandler.current
    val short = shortUrl(preview.url)
    // The computer's own localhost opens here through the tunnel, where the desktop has one.
    val tunnels = rememberTunnels(remote)
    val local = remember(preview.url) { LocalUrls.link(preview.url) != null }
    val link = remember(preview.url, tunnels) { opensOnPhone(preview.url) || (local && tunnels) }
    val open = { runCatching { uri.openUri(preview.url) }; Unit }
    QuietCard(Modifier.widthIn(max = 720.dp), padding = 0.dp, gap = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.weight(1f).heightIn(min = 36.dp).clip(RelayTheme.shapes.circle).background(c.surface)
                    .then(if (link) Modifier.pressable(open, scale = false) else Modifier)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TraceGap),
            ) {
                Icon(TablerIcons.Outline.World, null, size = TraceIcon, tint = c.inkFaint)
                Text(short, style = RelayTheme.type.meta, color = c.inkSoft, maxLines = 1)
            }
            if (link) CircleButton(
                TablerIcons.Outline.ExternalLink, if (local) "Open here through your computer" else "Open in your browser", open,
                size = CircleSize.Composer, tone = CircleTone.Clear,
            )
        }
        if (preview.shot == true) Frame(chatId, messageId, preview.title.ifEmpty { short }, open.takeIf { link })
    }
}

@Composable
private fun Frame(chatId: String, messageId: String, name: String, open: (() -> Unit)?) {
    val remote = LocalThreadRemote.current ?: return
    val source = remember(chatId, messageId) { ImageSource.Preview(chatId, messageId) }
    val wide = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val loaded = rememberImage(remote, source, wide)
    if (loaded.failed) return
    val bitmap = loaded.bitmap
    Box(
        Modifier.fillMaxWidth().height(FRAME_HEIGHT)
            .then(if (open != null) Modifier.pressable(open, scale = false) else Modifier)
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp).clip(RelayTheme.shapes.row),
    ) {
        if (bitmap != null) Image(
            bitmap, "Preview of $name", Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
        )
    }
}
