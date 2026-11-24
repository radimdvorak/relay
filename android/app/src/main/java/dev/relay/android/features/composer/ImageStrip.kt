package dev.relay.android.features.composer

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.features.composer.logic.FilePhase
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceLaptop
import io.github.lyxnx.compose.ui.tablericons.outline.File
import io.github.lyxnx.compose.ui.tablericons.outline.Movie
import io.github.lyxnx.compose.ui.tablericons.outline.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The photos (a tap draws on one), videos and files going with the message and the computer's screenshots in this draft. */
@Composable
internal fun ImageStrip(
    images: List<Attachment>,
    computerShots: List<Int>,
    onRemove: (Int) -> Unit,
    onEdit: (Int) -> Unit = {},
    files: List<PickedFile> = emptyList(),
    onRemoveFile: (String) -> Unit = {},
    onRetryFile: (String) -> Unit = {},
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        files.forEach { file ->
            Removable(file.name, onRemove = { onRemoveFile(file.key) }) { FileTile(file, onRetry = { onRetryFile(file.key) }) }
        }
        images.forEachIndexed { i, image ->
            Removable(image.name, onRemove = { onRemove(i) }) {
                Thumb(image.dataUrl, Modifier.pressable(onClick = { onEdit(i) }).semantics { contentDescription = "Draw on ${image.name}" })
            }
        }
        computerShots.forEach { n ->
            Tile(if (n != 0) "Screenshot $n, on your computer" else "Screenshot on your computer") {
                Icon(TablerIcons.Outline.DeviceLaptop, null, size = 18.dp, tint = RelayTheme.colors.inkSoft)
                Text(
                    "On computer", style = RelayTheme.type.small.copy(fontSize = 10.sp, lineHeight = 12.sp), color = RelayTheme.colors.inkSoft,
                    textAlign = TextAlign.Center, maxLines = 2,
                )
            }
        }
    }
}

/** An attachment with its ✕ in the corner; the ✕ takes taps a little past its circle. */
@Composable
private fun Removable(name: String, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val c = RelayTheme.colors
    Box {
        content()
        Box(
            Modifier.align(Alignment.TopEnd).pressable(onClick = onRemove).size(32.dp).padding(4.dp)
                .semantics { contentDescription = "Remove $name" },
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(Modifier.size(20.dp).background(c.strong, RelayTheme.shapes.circle), contentAlignment = Alignment.Center) {
                Icon(TablerIcons.Outline.X, null, size = 13.dp, tint = c.onStrong)
            }
        }
    }
}

/** A video or file: its name, and its size or how far it got; a failed upload tries again on a tap. */
@Composable
private fun FileTile(file: PickedFile, onRetry: () -> Unit) {
    val c = RelayTheme.colors
    val failed = file.phase == FilePhase.Failed
    val status = file.status
    Row(
        Modifier.height(TileSize).widthIn(max = 220.dp).clip(RelayTheme.shapes.field).background(c.tool)
            .then(if (failed) Modifier.pressable(onClick = onRetry) else Modifier)
            .semantics { contentDescription = if (status.isEmpty()) file.name else "${file.name}, $status" }
            .padding(start = 14.dp, end = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (file.video) TablerIcons.Outline.Movie else TablerIcons.Outline.File, null, size = 20.dp, tint = c.inkSoft)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(file.name, style = RelayTheme.type.meta, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status.isNotEmpty()) Text(
                status, style = RelayTheme.type.small,
                color = if (failed || file.phase == FilePhase.Refused) c.danger else c.inkSoft, maxLines = 1,
            )
        }
    }
}

@Composable
private fun Tile(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        Modifier.size(TileSize).clip(RelayTheme.shapes.field).background(RelayTheme.colors.tool).then(modifier)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** Drawn from the prepared data URL: a camera shot's gallery copy is gone once it's read. */
@Composable
private fun Thumb(dataUrl: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, dataUrl) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 192) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(TileSize).clip(RelayTheme.shapes.field).background(RelayTheme.colors.tool).then(modifier)) {
        bitmap?.let { Image(it, null, Modifier.size(TileSize), contentScale = ContentScale.Crop) }
    }
}

private val TileSize = 64.dp
