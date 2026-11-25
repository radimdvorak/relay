package dev.relay.android.features.composer

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.relay.android.features.composer.logic.ModelCatalogs
import dev.relay.android.features.composer.logic.offeredAgents
import dev.relay.android.remote.client.RemoteClient

/** The agents a composer offers and the models each lists, as far as they're known. */
internal class AgentLists(
    val models: ModelCatalogs,
    /** In Settings' order, without the ones switched off but this composer's own. */
    val offered: List<String>,
    /** Lists the models of [wanted] agents and answers with all that are known. */
    val load: suspend (wanted: List<String>) -> ModelCatalogs,
)

/** Keeps them for a composer sending to [provider]; a [picking] one asks for the agents again. */
@Composable
internal fun rememberAgentLists(
    client: RemoteClient?,
    computer: String?,
    connection: Int,
    online: Boolean,
    provider: String,
    picking: Boolean,
    context: Context,
): AgentLists {
    // Held for the connection, so the chips name the model at once.
    var models by remember(computer, connection) { mutableStateOf(ModelLists.known(computer, connection)) }
    val saves = remember { ComposerSaves.of(context.cacheDir) }
    val connected by rememberUpdatedState(client)
    LaunchedEffect(computer) {
        val pc = computer ?: return@LaunchedEffect
        if (ModelLists.readSaved(saves, pc)) models = ModelLists.known(pc, connection)
    }
    suspend fun load(wanted: List<String>): ModelCatalogs {
        val cl = connected ?: return models
        val pc = computer ?: return models
        return ModelLists.load(cl, pc, connection, wanted, saves).also { models = it }
    }
    LaunchedEffect(client, computer, provider, online, connection) { runCatching { load(listOf(provider)) } }

    var prefs by remember(computer) { mutableStateOf(AgentPrefs.known(computer)) }
    // Settings → Agents may have changed since the composer asked.
    LaunchedEffect(client, computer, online, connection, picking) {
        prefs = AgentPrefs.load(client ?: return@LaunchedEffect, computer ?: return@LaunchedEffect)
    }
    return AgentLists(models, offeredAgents(prefs?.agentOrder.orEmpty(), prefs?.agentsOff.orEmpty(), provider), ::load)
}
