package dev.relay.android.features.composer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.relay.android.features.composer.logic.AgentModel
import dev.relay.android.features.composer.logic.ModelCatalogs
import dev.relay.android.features.composer.logic.agentName
import dev.relay.android.features.composer.logic.matchRanges
import dev.relay.android.features.composer.logic.searchModels
import dev.relay.android.ui.ListInset
import dev.relay.android.ui.Icon
import dev.relay.android.ui.RowTextInset
import dev.relay.android.ui.SearchField
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Check

/** Every agent's models, narrowed as you type; a tap picks one, its agent with it. */
@Composable
internal fun ModelSearch(
    query: String,
    onQuery: (String) -> Unit,
    agents: List<String>,
    catalogs: ModelCatalogs,
    isChosen: (provider: String, model: AgentModel) -> Boolean,
    onPick: (provider: String, model: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val found = remember(query, agents, catalogs) { searchModels(query, agents, catalogs) }
    Column(modifier.fillMaxWidth().heightIn(min = SheetPageHeight).imePadding()) {
        SearchField(query, onQuery, Modifier.padding(horizontal = 16.dp), "Search models", focus)
        if (found.isEmpty()) Text(
            if (catalogs.isEmpty()) "No models listed yet." else "No model matches.",
            Modifier.padding(horizontal = ListInset + RowTextInset, vertical = 20.dp),
            style = RelayTheme.type.meta, color = RelayTheme.colors.inkSoft,
        )
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = ListInset)) {
            found.forEach { (provider, models) ->
                item("agent:$provider") { SectionLabel(agentName(provider)) }
                items(models, key = { "$provider:${it.id}" }) { model ->
                    FoundModel(model, query, isChosen(provider, model)) { onPick(provider, model.id) }
                }
            }
        }
    }
}

@Composable
private fun FoundModel(model: AgentModel, query: String, chosen: Boolean, onClick: () -> Unit) {
    val c = RelayTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RelayTheme.shapes.row)
            .pressable(onClick, role = Role.RadioButton, scale = false)
            .semantics { selected = chosen }
            .heightIn(min = 48.dp)
            .padding(horizontal = RowTextInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            matched(model.name, query), Modifier.weight(1f),
            style = RelayTheme.type.row.copy(color = c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val meta = if (model.legacy == true) "Older" else model.group
        if (!meta.isNullOrEmpty()) Text(meta, Modifier.padding(start = 12.dp), style = RelayTheme.type.meta, color = c.inkSoft, maxLines = 1)
        if (chosen) Icon(TablerIcons.Outline.Check, "Chosen", Modifier.padding(start = 12.dp), tint = c.ink, size = 18.dp)
    }
}

private fun matched(name: String, query: String) = AnnotatedString.Builder(name).apply {
    matchRanges(name, query).forEach { addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), it.first, it.last + 1) }
}.toAnnotatedString()
