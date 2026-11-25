package dev.relay.android.features.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Text
import dev.relay.android.ui.ValueSlider
import dev.relay.android.ui.theme.DEFAULT_CONTRAST
import dev.relay.android.ui.theme.RelayTheme
import dev.relay.android.ui.theme.ThemeSettings

/** The phone's own contrast over whichever theme it wears; the whole app recolours as it moves. */
@Composable
internal fun ContrastRow(theme: ThemeSettings) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Contrast", Modifier.weight(1f), style = RelayTheme.type.row)
                ResetButton(changed = theme.contrast != DEFAULT_CONTRAST) { theme.contrast = DEFAULT_CONTRAST }
                Text("${theme.contrast}", Modifier.widthIn(min = 32.dp), style = RelayTheme.type.row, textAlign = TextAlign.End)
            }
            Text("On top of the theme, on this phone only.", style = RelayTheme.type.meta, color = RelayTheme.colors.inkSoft)
        }
        ValueSlider(
            theme.contrast,
            { theme.contrast = it },
            Modifier.semantics { contentDescription = "Contrast" },
            marked = DEFAULT_CONTRAST,
        )
    }
}

/** Keeps its room while there's nothing to reset: coming and going would move the slider under the finger. */
@Composable
private fun ResetButton(changed: Boolean, onClick: () -> Unit) {
    val shown by animateFloatAsState(if (changed) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "reset")
    Button(
        "Reset",
        onClick,
        Modifier.graphicsLayer { alpha = shown }.then(if (changed) Modifier else Modifier.clearAndSetSemantics {}),
        variant = ButtonVariant.Secondary,
        size = ButtonSize.Sm,
        enabled = changed,
    )
}
