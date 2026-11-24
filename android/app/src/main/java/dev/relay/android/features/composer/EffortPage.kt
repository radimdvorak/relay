package dev.relay.android.features.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.features.composer.logic.EffortLevels
import dev.relay.android.features.composer.logic.effortLabel
import dev.relay.android.ui.Chip
import dev.relay.android.ui.EffortSlider
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme

/** The effort sheet: the level in large, the slider under it, and the way back to Default. */
@Composable
internal fun EffortPage(
    levels: EffortLevels?,
    effort: String,
    onEffort: (String) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val c = RelayTheme.colors
    Column(
        modifier.fillMaxWidth().heightIn(min = SheetPageHeight).verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        if (levels == null && loading) {
            Spinner(color = c.inkSoft)
            return@Column
        }
        if (levels == null) {
            Note("This model has no effort to set.")
            return@Column
        }
        val level = levels.indexOf(effort)
        // The label follows the thumb while it's dragged, ahead of the pick.
        var preview by remember(levels, level) { mutableIntStateOf(level) }
        val named = effortLabel(levels.values[preview])
        AnimatedContent(
            named,
            Modifier.fillMaxWidth().clearAndSetSemantics {},
            transitionSpec = { fadeIn(tween(100, 40)) togetherWith fadeOut(tween(60)) using SizeTransform(clip = false) },
            contentAlignment = Alignment.Center,
            label = "effort",
        ) { label ->
            Text(
                label, Modifier.fillMaxWidth(),
                style = RelayTheme.type.sheetTitle.copy(fontSize = 22.sp, lineHeight = 28.sp), textAlign = TextAlign.Center, maxLines = 1,
            )
        }
        Spacer(Modifier.height(14.dp))
        if (levels.choosable) EffortSlider(
            level,
            { onEffort(levels.values[it]) },
            Modifier.padding(horizontal = 20.dp),
            levels = levels.values.size,
            marked = levels.default,
            onPreview = { preview = it },
            describe = { effortLabel(levels.values[it]) },
        )
        Box(Modifier.heightIn(min = 72.dp).padding(top = 20.dp), contentAlignment = Alignment.TopCenter) {
            val default = levels.default?.let { effortLabel(levels.values[it]) }
            when {
                default == null -> {}
                levels.followsDefault(effort) -> Text("The default for this model", style = RelayTheme.type.meta, color = c.inkSoft)
                else -> Chip("Back to the default, $default", { onEffort("") })
            }
        }
        Spacer(Modifier.height(20.dp))
        Note(
            if (levels.choosable) "Higher effort means more thorough answers, but takes longer and uses your limits faster."
            else "This model runs at one effort.",
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(horizontal = 30.dp), style = RelayTheme.type.body, color = RelayTheme.colors.inkSoft)
}

/** The model sheet's pages are at least this tall. */
internal val SheetPageHeight = 388.dp
