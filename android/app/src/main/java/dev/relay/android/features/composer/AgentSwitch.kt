package dev.relay.android.features.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.relay.android.features.composer.logic.NewThreadModels
import dev.relay.android.features.composer.logic.switchAgent
import dev.relay.android.features.composer.logic.withRememberedModel
import dev.relay.android.remote.model.ResumeSettings

private data class Pick(val model: String, val fast: Boolean, val effort: String, val contextWindow: String?)

/**
 * Settings moved to another agent. Each keeps its own model while you switch between them, like
 * the desktop's slots; one not picked here yet starts on the model [remembered] for it.
 */
@Composable
internal fun rememberAgentSwitch(settings: ResumeSettings, remembered: NewThreadModels): (ResumeSettings, String) -> ResumeSettings {
    val picks = remember { mutableMapOf<String, Pick>() }
    SideEffect {
        picks[settings.provider] = settings.choice.let { Pick(it.model, it.fast, it.reasoningEffort, settings.contextWindow) }
    }
    val fresh = rememberUpdatedState(remembered)
    return remember {
        switch@{ next, to ->
            if (to == next.provider) return@switch next
            val kept = picks[to]
            val other = switchAgent(next, to)
            if (kept != null) other.copy(
                choice = other.choice.copy(model = kept.model, fast = kept.fast, reasoningEffort = kept.effort),
                contextWindow = kept.contextWindow ?: other.contextWindow,
            ) else withRememberedModel(other, fresh.value)
        }
    }
}
