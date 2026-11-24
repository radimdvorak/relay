package dev.relay.android.features.composer.logic

/** One row of the model wheel; "" is Default. [levels] is null for a model without efforts. */
data class ModelRow(val id: String, val name: String, val description: String, val levels: EffortLevels? = null)

/**
 * An agent's models as the wheel lists them: Default, the thread's own model when the list lacks
 * it, the current ones, then the older ones.
 */
fun modelRows(provider: String, models: List<AgentModel>, chosen: String, fallback: AgentDefaults?): List<ModelRow> = buildList {
    val fallbackId = fallback?.model.orEmpty()
    val fallbackModel = listedModel(provider, models, fallbackId)
    val fallbackName = fallbackModel?.name ?: fallbackId
    add(
        ModelRow(
            "", "Default",
            if (fallbackName.isNotEmpty()) "Uses $fallbackName" else "The agent's own choice",
            // On Default, the effort applies to whichever model the agent falls back to.
            effortLevels(fallbackModel?.efforts.orEmpty(), fallback?.effort?.ifEmpty { null } ?: fallbackModel?.defaultEffort),
        ),
    )
    val unlisted = chosen.isNotEmpty() && listedModel(provider, models, chosen) == null
    if (unlisted) add(ModelRow(chosen, chosen, "What this thread last used"))
    for (model in models.filter { it.legacy != true } + models.filter { it.legacy == true }) {
        val default = fallback?.efforts?.get(model.id)?.ifEmpty { null } ?: model.defaultEffort
        add(ModelRow(model.id, model.name, describe(model), effortLevels(model.efforts, default)))
    }
}

private fun describe(model: AgentModel): String =
    listOfNotNull(
        "Older model".takeIf { model.legacy == true },
        model.group?.ifEmpty { null },
        model.description.ifEmpty { null },
    ).joinToString(" · ")

/** The row a chosen id stands for, whichever context window it asks for. */
fun chosenRow(provider: String, models: List<AgentModel>, chosen: String): String =
    listedModel(provider, models, chosen)?.id ?: chosen

/** Every listed model matching [query] (each word, in its name, id, group or agent), under its agent in [agents]' order. */
fun searchModels(query: String, agents: List<String>, catalogs: ModelCatalogs): List<Pair<String, List<AgentModel>>> {
    val words = query.lowercase().split(' ').filter { it.isNotEmpty() }
    return agents.mapNotNull { agent ->
        val found = catalogs[agent].orEmpty().filter { model ->
            val text = "${model.name} ${model.id} ${model.group.orEmpty()} ${agentName(agent)}".lowercase()
            words.all { it in text }
        }
        if (found.isEmpty()) null else agent to found
    }
}

/** Where the query's words sit in [name], to show in bold; overlapping words merge. */
fun matchRanges(name: String, query: String): List<IntRange> {
    val found = query.lowercase().split(' ').filter { it.isNotEmpty() }.mapNotNull { word ->
        val at = name.lowercase().indexOf(word)
        if (at < 0) null else at until at + word.length
    }.sortedBy { it.first }
    return found.fold(emptyList()) { merged, next ->
        val last = merged.lastOrNull()
        if (last == null || next.first > last.last + 1) merged + listOf(next)
        else merged.dropLast(1) + listOf(last.first..maxOf(last.last, next.last))
    }
}
