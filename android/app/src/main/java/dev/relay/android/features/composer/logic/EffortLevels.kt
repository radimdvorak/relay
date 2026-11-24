package dev.relay.android.features.composer.logic

/**
 * The effort sheet's stops for one model, in the order the slider shows them. "" is Default: a
 * stop of its own only while nobody says which level Default is, otherwise [default] marks the
 * level it stands for.
 */
data class EffortLevels(val values: List<String>, val default: Int?) {
    /** With one stop there's nothing to slide between. */
    val choosable get() = values.size > 1

    /** The stop a chosen effort sits on; Default, and a level this model lacks, sit on its default. */
    fun indexOf(effort: String): Int = values.indexOf(effort).takeIf { it >= 0 } ?: default ?: 0

    fun label(effort: String): String = effortLabel(values[indexOf(effort)])

    /** Whether [effort] leaves the level to the agent, rather than naming one. */
    fun followsDefault(effort: String) = effort.isEmpty() || effort !in values
}

/** Null when the model takes no effort at all. */
fun effortLevels(efforts: List<String>, defaultEffort: String?): EffortLevels? {
    val levels = efforts.filter { it.isNotEmpty() }.distinct()
    if (levels.isEmpty()) return null
    val default = levels.indexOf(defaultEffort).takeIf { it >= 0 }
    return if (default != null) EffortLevels(levels, default) else EffortLevels(listOf("") + levels, null)
}

/** Whether the composer offers an effort for what it sends with; on Default, any listed model's answer will do. */
fun offersEffort(provider: String, model: String, catalogs: ModelCatalogs): Boolean =
    if (model.isEmpty()) catalogs[provider].orEmpty().any { it.efforts.isNotEmpty() }
    else modelEfforts(provider, model, catalogs).isNotEmpty()
