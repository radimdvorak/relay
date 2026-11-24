package dev.relay.android.features.search

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.features.home.ProjectIcon
import dev.relay.android.features.home.ago
import dev.relay.android.features.home.agentsSince
import dev.relay.android.features.home.shortAge
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteProject
import dev.relay.android.ui.Icon
import dev.relay.android.ui.RowTextInset
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.Text
import dev.relay.android.ui.listPadding
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayMotion
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.GitPullRequest
import io.github.lyxnx.compose.ui.tablericons.outline.HelpCircle
import io.github.lyxnx.compose.ui.tablericons.outline.Pin
import androidx.compose.material3.Text as MaterialText

private const val DAY_MS = 86_400_000L

/** Rows arriving together come in one after the other, the later ones all at once. */
private const val STAGGER_MS = 25
private const val STAGGERED = 8

/** What search found so far, the list thinning out and closing up as the query grows. */
@Composable
internal fun SearchResults(
    query: String,
    found: Found,
    now: Long,
    onThread: (RemoteChatSummary) -> Unit,
    onProject: (RemoteProject) -> Unit,
    bottom: Dp,
    modifier: Modifier = Modifier,
    /** Looks through pull requests as well, when they aren't yet. */
    onSearchPulls: (() -> Unit)? = null,
    /** Pull requests, above the rest. */
    pulls: (LazyListScope.(terms: List<String>) -> Unit)? = null,
) {
    val terms = searchTerms(query)
    val list = rememberLazyListState()
    LaunchedEffect(query) { list.scrollToItem(0) }
    LazyColumn(modifier.fillMaxSize(), list, contentPadding = listPadding(bottom = bottom, top = 8.dp)) {
        pulls?.invoke(this, terms)
        if (found.projects.isNotEmpty()) {
            item(key = "projects") { SectionLabel("Projects", Modifier.animateItem()) }
            itemsIndexed(found.projects, key = { _, p -> "project-${p.id}" }) { i, project ->
                SearchRow(
                    project.name, terms, { onProject(project) }, arriving(i),
                    leading = { ProjectIcon(project, size = 20.dp) },
                )
            }
        }
        val labelled = found.projects.isNotEmpty() || pulls != null
        if (labelled && found.threads.isNotEmpty()) item(key = "threads") { SectionLabel("Threads", Modifier.animateItem()) }
        itemsIndexed(found.threads, key = { _, t -> "thread-${t.chat.id}" }) { i, thread -> ThreadFound(thread, terms, now, arriving(i)) { onThread(thread.chat) } }
        val nothing = found.threads.isEmpty() && found.projects.isEmpty()
        if (nothing && pulls == null) item(key = "none") {
            SearchNote(if (terms.isEmpty()) "No threads yet." else "No thread or project matches “${query.trim()}”.", Modifier.animateItem())
        }
        if (onSearchPulls != null && terms.isNotEmpty()) item(key = "more-pulls") {
            SearchRow(
                "Search pull requests for “${query.trim()}”", emptyList(), onSearchPulls, arriving(STAGGERED),
                titleColor = RelayTheme.colors.inkSoft,
                leading = { Icon(TablerIcons.Outline.GitPullRequest, null, tint = RelayTheme.colors.inkSoft, size = 20.dp) },
            )
        }
    }
}

@Composable
internal fun LazyItemScope.arriving(index: Int): Modifier = Modifier.animateItem(
    fadeInSpec = tween(160, delayMillis = STAGGER_MS * index.coerceAtMost(STAGGERED)),
    placementSpec = RelayMotion.standard.defaultSpatialSpec(),
    fadeOutSpec = tween(120),
)

@Composable
internal fun SearchNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().padding(horizontal = RowTextInset, vertical = 28.dp),
        style = RelayTheme.type.body,
        color = RelayTheme.colors.inkSoft,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ThreadFound(found: FoundThread, terms: List<String>, now: Long, modifier: Modifier, onClick: () -> Unit) {
    val c = RelayTheme.colors
    val chat = found.chat
    SearchRow(
        chat.title, terms, onClick, modifier,
        subtitle = found.project.takeIf { found.byProject },
        meta = {
            val working = chat.running == true || agentsSince(chat.pending) != null
            if (chat.pinnedAt != null) Icon(TablerIcons.Outline.Pin, "Pinned", tint = c.inkSoft, size = 18.dp)
            when {
                chat.waiting == true -> Icon(TablerIcons.Outline.HelpCircle, "Needs you", tint = c.waiting, size = 18.dp)
                working -> Spinner(size = 14.dp, color = c.inkSoft)
            }
            Text(
                if (now - chat.updated < 7 * DAY_MS) shortAge(chat.updated, now) else ago(chat.updated, now),
                style = RelayTheme.type.meta,
                color = c.inkSoft,
                maxLines = 1,
            )
        },
    )
}

/** A 48dp result: its title with what matched in bold, a subtitle only when the match is there, small print at the end. */
@Composable
internal fun SearchRow(
    title: String,
    terms: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = RelayTheme.colors.ink,
    leading: (@Composable () -> Unit)? = null,
    meta: (@Composable () -> Unit)? = null,
) {
    val c = RelayTheme.colors
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    Row(
        modifier.fillMaxWidth().clip(RelayTheme.shapes.row)
            .background(if (pressed) c.selected else Color.Transparent)
            .pressable(onClick, scale = false, interaction = press)
            .heightIn(min = 48.dp).padding(horizontal = RowTextInset, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { leading() }
        Column(Modifier.weight(1f)) {
            MatchedText(title, terms, RelayTheme.type.listTitle, titleColor)
            if (subtitle != null) MatchedText(subtitle, terms, RelayTheme.type.listMeta, c.inkSoft)
        }
        meta?.invoke()
    }
}

@Composable
private fun MatchedText(text: String, terms: List<String>, style: TextStyle, color: Color) {
    MaterialText(remember(text, terms) { matched(text, terms) }, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun matched(text: String, terms: List<String>): AnnotatedString = buildAnnotatedString {
    append(text)
    for (range in matchRanges(text, terms)) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), range.first, range.last + 1)
}
