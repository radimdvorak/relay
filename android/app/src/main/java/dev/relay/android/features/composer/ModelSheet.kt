package dev.relay.android.features.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.relay.android.features.composer.logic.AgentDefaults
import dev.relay.android.features.composer.logic.AgentModel
import dev.relay.android.features.composer.logic.ModelCatalogs
import dev.relay.android.features.composer.logic.NewThreadModel
import dev.relay.android.features.composer.logic.agentName
import dev.relay.android.features.composer.logic.chosenRow
import dev.relay.android.features.composer.logic.listedModel
import dev.relay.android.features.composer.logic.modelRows
import dev.relay.android.features.composer.logic.onWindow
import dev.relay.android.features.composer.logic.supportsFast
import dev.relay.android.remote.client.RemoteClient
import dev.relay.android.remote.model.ResumeSettings
import dev.relay.android.ui.CircleButton
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Search
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** The model sheet's pages; Effort and Search lead back to Models. */
enum class ModelSheetPage(val title: String) { Models("Model"), Effort("Effort"), Search("Model") }

private data class Loaded(val list: List<AgentModel>? = null, val fallback: AgentDefaults? = null, val error: String? = null)

/** What the sheet may change; the composer decides how each lands in its settings. */
internal class ModelSheetActions(
    val settings: (ResumeSettings) -> Unit,
    /** Another agent, on the model the composer kept for it. */
    val agent: (String) -> Unit,
    /** A model of any agent; [ModelCatalogs] as the sheet knows them. */
    val model: (provider: String, model: String, known: ModelCatalogs) -> Unit,
    /** Lists every agent's models, for the search. */
    val loadAll: suspend () -> Unit,
)

/** The desktop's model picker, for a thumb: agents, their models on a wheel, effort, and Fast or the 200k window. */
@Composable
internal fun ModelSheet(
    client: RemoteClient?,
    online: Boolean,
    projectId: String,
    settings: ResumeSettings,
    /** Lists the composer already has, shown while the sheet asks again. */
    known: ModelCatalogs,
    /** The agents to switch between, in Settings' order. */
    agents: List<String>,
    actions: ModelSheetActions,
    onDismiss: () -> Unit,
    start: ModelSheetPage = ModelSheetPage.Models,
) {
    val provider = settings.provider
    var page by remember { mutableStateOf(start) }
    var query by remember { mutableStateOf("") }
    val result = loadedModels(client, online, projectId, provider)
    val list = result?.list?.takeIf { it.isNotEmpty() } ?: known[provider] ?: if (result != null) emptyList() else null
    val rows = list?.let { modelRows(provider, it, settings.choice.model, result?.fallback) }
    val chosen = chosenRow(provider, list.orEmpty(), settings.choice.model)
    val catalogs = if (list == null) known else known + (provider to list)
    if (page == ModelSheetPage.Search) LaunchedEffect(Unit) { actions.loadAll() }

    val searchable = agents.size > 1 || (rows?.size ?: 0) > 6
    ComposerSheet(
        page.title, onDismiss,
        onBack = if (page == ModelSheetPage.Models) null else ({ page = ModelSheetPage.Models }),
        action = if (page != ModelSheetPage.Models || !searchable) null else ({
            CircleButton(TablerIcons.Outline.Search, "Search models", { page = ModelSheetPage.Search })
        }),
    ) {
        AnimatedContent(page, transitionSpec = {
            val from = if (targetState == ModelSheetPage.Models) -1 else 1
            fadeIn(tween(120)) + slideInHorizontally(tween(180)) { from * it / 16 } togetherWith fadeOut(tween(100))
        }, label = "model-sheet") { shown ->
            when (shown) {
                ModelSheetPage.Models -> ModelPage(
                    agents, provider, actions.agent, rows, chosen, settings.choice.reasoningEffort,
                    onModel = { actions.model(provider, it, catalogs) },
                    onEffort = { page = ModelSheetPage.Effort },
                    toggles = modelToggles(settings, list, result?.fallback, actions.settings),
                    error = result?.error,
                )
                ModelSheetPage.Effort -> EffortPage(
                    rows?.find { it.id == chosen }?.levels, settings.choice.reasoningEffort,
                    { actions.settings(settings.copy(choice = settings.choice.copy(reasoningEffort = it))) },
                    loading = rows == null,
                )
                ModelSheetPage.Search -> ModelSearch(
                    query, { query = it }, agents, catalogs,
                    isChosen = { agent, model -> agent == provider && model.id == chosen },
                    onPick = { agent, model ->
                        actions.model(agent, model, catalogs)
                        page = ModelSheetPage.Models
                    },
                )
            }
        }
    }
}

@Composable
private fun loadedModels(client: RemoteClient?, online: Boolean, projectId: String, provider: String): Loaded? {
    var loaded by remember(client, provider, projectId) { mutableStateOf<Loaded?>(null) }
    // Defaults are project-specific; reconnecting or reopening refreshes both.
    LaunchedEffect(client, provider, projectId, online) {
        if (client == null) return@LaunchedEffect
        loaded = try {
            coroutineScope {
                val defaults = async { runCatching { client.agentDefaults(projectId, provider) }.getOrNull() }
                val list = client.agentModels(provider)
                Loaded(list, defaults.await())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Default and the thread's own model still work without the list.
            Loaded(error = e.message ?: e.toString())
        }
    }
    return loaded
}

private fun modelToggles(
    settings: ResumeSettings,
    list: List<AgentModel>?,
    fallback: AgentDefaults?,
    onSettings: (ResumeSettings) -> Unit,
): List<ModelToggle> {
    val provider = settings.provider
    val choice = settings.choice
    val runs = listedModel(provider, list, choice.model.ifEmpty { fallback?.model.orEmpty() })
    return buildList {
        if (supportsFast(provider, choice.model, list)) add(
            ModelToggle("Fast", "${agentName(provider)}'s faster service tier; uses more of your limit.", choice.fast) {
                onSettings(settings.copy(choice = choice.copy(fast = it)))
            },
        )
        if (provider == "claude" && runs?.longContext == true) add(
            ModelToggle(
                "200k context window", "Off leaves Claude on its default window, 1M on most models.", settings.contextWindow == "200k",
            ) { on ->
                val next = onWindow(NewThreadModel(choice, settings.contextWindow), if (on) "200k" else "1m")
                onSettings(settings.copy(choice = next.choice, contextWindow = next.contextWindow))
            },
        )
    }
}
