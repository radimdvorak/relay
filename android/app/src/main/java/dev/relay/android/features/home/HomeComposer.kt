package dev.relay.android.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.relay.android.lib.navigation.ComposerStart
import dev.relay.android.lib.navigation.NEW_THREAD_MORPH
import dev.relay.android.lib.navigation.NextComposerStart
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.ComposerPill
import dev.relay.android.ui.ComposerPillHeight
import dev.relay.android.ui.ComposerPlaceholder
import dev.relay.android.ui.pressable
import dev.relay.android.ui.sharedBox
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Microphone
import io.github.lyxnx.compose.ui.tablericons.outline.Plus

/**
 * Home's composer while it's only a promise of one: tapped, it grows into the new-thread screen's
 * composer. Plus opens that on what to attach, the mic on dictating.
 */
@Composable
internal fun HomeComposer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    fun open(start: ComposerStart) {
        NextComposerStart.ask(start)
        onOpen()
    }
    Box(
        modifier.fillMaxWidth()
            .sharedBox(NEW_THREAD_MORPH, RelayTheme.shapes.composer)
            .background(RelayTheme.colors.raised, RelayTheme.shapes.composer)
            .semantics { contentDescription = "New thread" }
            .pressable({ open(ComposerStart.Type) }, scale = false),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ComposerPill(
            field = { ComposerPlaceholder("Start a new thread", Modifier.fillMaxWidth()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = ComposerPillHeight),
            leading = { CircleButton(TablerIcons.Outline.Plus, "Start a thread with an attachment", { open(ComposerStart.Attach) }, size = CircleSize.Composer) },
            trailing = { CircleButton(TablerIcons.Outline.Microphone, "Dictate a new thread", { open(ComposerStart.Dictate) }, size = CircleSize.Composer) },
        )
    }
}
