package dev.relay.android.features.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import dev.relay.android.features.sketch.SketchEditor
import dev.relay.android.features.sketch.Sketched
import kotlinx.coroutines.launch

/**
 * The sketch editor over one of the draft's photos. It opens on the photo as
 * it was picked with the strokes so far, and hands back the photo with the new
 * ink burned in, or the untouched photo once every stroke is undone.
 */
@Composable
internal fun DrawOnImage(image: Attachment, onDrawn: (Attachment) -> Unit, onError: (String) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val original = image.sketch?.original ?: image.dataUrl
    SketchEditor(
        source = original,
        strokes = image.sketch?.strokes.orEmpty(),
        onCancel = onClose,
        onDone = { strokes, burned ->
            if (burned == null) {
                onDrawn(image.copy(dataUrl = original, mimeType = mimeOf(original), sketch = null))
                onClose()
                return@SketchEditor
            }
            scope.launch {
                try {
                    val dataUrl = jpegDataUrl(burned)
                    onDrawn(image.copy(dataUrl = dataUrl, mimeType = "image/jpeg", sketch = Sketched(original, strokes)))
                } catch (e: Exception) {
                    onError(e.message ?: "Couldn't save the drawing.")
                } finally {
                    burned.recycle()
                    onClose()
                }
            }
        },
    )
}

private fun mimeOf(dataUrl: String) = dataUrl.substringAfter("data:").substringBefore(";")
