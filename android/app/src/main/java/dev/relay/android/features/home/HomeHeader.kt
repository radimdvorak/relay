package dev.relay.android.features.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.ChromeHeader
import dev.relay.android.ui.CircleAction
import dev.relay.android.ui.CirclePair
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.LocalPaneToggle
import dev.relay.android.ui.Text
import dev.relay.android.ui.fill
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Search
import io.github.lyxnx.compose.ui.tablericons.outline.Settings

/** What the pair at the header's end takes, with the gap before the name; the filter stands beside it. */
private val PAIR_ROOM = 100.dp
private val FILTER_ROOM = 52.dp

internal val HomeTab.title: String get() = when (this) {
    HomeTab.Threads -> "Threads"
    HomeTab.Projects -> "Projects"
    HomeTab.Pulls -> "Pull requests"
}

/**
 * Home's chrome: the computer's name to switch it, the filter and the search | settings pair,
 * and under them the tab's title on a line of its own. A new thread starts in the composer below.
 */
@Composable
internal fun HomeHeader(
    tab: HomeTab,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    computer: @Composable () -> Unit,
    hasFilter: Boolean,
    filter: @Composable () -> Unit,
) {
    val motion = RelayTheme.motion.standard
    Column(Modifier.fillMaxWidth().background(RelayTheme.colors.chrome)) {
        ChromeHeader(
            leading = {
                Row(
                    Modifier.padding(start = 4.dp, end = PAIR_ROOM + if (hasFilter) FILTER_ROOM else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LocalPaneToggle.current?.invoke()
                    computer()
                }
            },
            trailing = {
                AnimatedVisibility(hasFilter, enter = fadeIn(motion.defaultEffectsSpec()), exit = fadeOut(motion.fastEffectsSpec())) {
                    Box(
                        Modifier.height(44.dp).widthIn(min = 44.dp).clip(RelayTheme.shapes.circle).background(CircleTone.Raised.fill(RelayTheme.colors)),
                        contentAlignment = Alignment.Center,
                    ) { filter() }
                }
                CirclePair(
                    CircleAction(TablerIcons.Outline.Search, "Search", onSearch),
                    CircleAction(TablerIcons.Outline.Settings, "Settings", onSettings),
                )
            },
        )
        val effects = motion.defaultEffectsSpec<Float>()
        AnimatedContent(tab, transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) }, label = "home-title") { shown ->
            Text(
                shown.title,
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                style = RelayTheme.type.title,
                maxLines = 1,
            )
        }
    }
}
