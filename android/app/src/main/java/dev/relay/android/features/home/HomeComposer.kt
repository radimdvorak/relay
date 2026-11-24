package dev.relay.android.features.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.relay.android.lib.navigation.NEW_THREAD_MORPH
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.ComposerPill
import dev.relay.android.ui.ComposerPlaceholder
import dev.relay.android.ui.pressable
import dev.relay.android.ui.sharedButton
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowUp

/** Home's composer, for now a way into the new-thread screen: tapping it calls [onOpen]. */
@Composable
internal fun HomeComposer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    ComposerPill(
        field = { ComposerPlaceholder("What should we work on?") },
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "New thread" }.pressable(onOpen, scale = false),
        trailing = {
            CircleButton(
                TablerIcons.Outline.ArrowUp, "New thread", onOpen,
                Modifier.sharedButton(NEW_THREAD_MORPH), CircleSize.Composer, CircleTone.Strong,
            )
        },
    )
}
