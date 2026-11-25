package dev.relay.android.features.search

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.features.home.ago
import dev.relay.android.features.home.shortAge
import dev.relay.android.features.pulls.ListedPull
import dev.relay.android.features.pulls.PullStateIcon
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.SegmentedTabs
import dev.relay.android.ui.TabOption
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme

private val STATES = listOf(TabOption("open", "Open"), TabOption("closed", "Closed"), TabOption("all", "All"))

/** The pull requests every term is found in: its title, its repository and number, or its author. */
internal fun searchPulls(terms: List<String>, pulls: List<ListedPull>): List<ListedPull> = pulls.filter { p ->
    val line = pullLine(p)
    terms.all { p.hit.title.contains(it, ignoreCase = true) || line.contains(it, ignoreCase = true) }
}

internal fun pullLine(p: ListedPull) =
    listOf("${p.hit.repository.name} #${p.hit.number}", p.hit.user.login).filter { it.isNotEmpty() }.joinToString(" · ")

/** Search's pull requests: which state of them, then the ones that match. [note] says why there are none yet. */
internal fun LazyListScope.pullResults(
    terms: List<String>,
    pulls: List<ListedPull>,
    state: String,
    onState: (String) -> Unit,
    note: String?,
    now: Long,
    onOpen: (ListedPull) -> Unit,
) {
    item(key = "pulls") { SectionLabel("Pull requests") }
    item(key = "pull-states") {
        SegmentedTabs(STATES, state, onState, Modifier.padding(horizontal = 8.dp).padding(top = 4.dp, bottom = 8.dp))
    }
    val shown = searchPulls(terms, pulls)
    itemsIndexed(shown, key = { _, p -> "pull-${p.key}" }) { i, p ->
        SearchRow(
            p.hit.title, terms, { onOpen(p) }, arriving(i),
            subtitle = pullLine(p),
            leading = { PullStateIcon(p.hit.state, p.hit.draft == true, p.merged, size = 20.dp) },
            meta = {
                Text(
                    if (now - p.updated < 7 * 86_400_000L) shortAge(p.updated, now) else ago(p.updated, now),
                    style = RelayTheme.type.meta,
                    color = RelayTheme.colors.inkSoft,
                    maxLines = 1,
                )
            },
        )
    }
    val none = note ?: "No pull request matches.".takeIf { shown.isEmpty() && terms.isNotEmpty() }
        ?: "No pull requests of yours.".takeIf { shown.isEmpty() }
    if (none != null) item(key = "no-pulls") { SearchNote(none) }
}
