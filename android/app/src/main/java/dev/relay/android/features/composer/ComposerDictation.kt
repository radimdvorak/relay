package dev.relay.android.features.composer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.relay.android.features.dictation.Dictation
import dev.relay.android.features.dictation.DictationInput
import dev.relay.android.features.dictation.DictationState
import dev.relay.android.remote.Remote
import kotlinx.coroutines.flow.first

/** One composer's share of the phone's dictation: whether the words are coming here, and ending them. */
internal class ComposerDictation(val dictation: Dictation, val owner: Any, state: DictationState) {
    val active = state.owner === owner && state.phase != DictationState.Phase.Idle
    val listening = active && state.phase == DictationState.Phase.Listening
    val error = if (state.owner === owner) state.error else null

    /** As it is now, rather than when the composer last drew. */
    val activeNow: Boolean
        get() = dictation.state.value.let { it.owner === owner && it.phase != DictationState.Phase.Idle }

    /** Lets this composer's dictation finish; false when it can't yet (still starting). */
    suspend fun finish(): Boolean {
        if (!activeNow) return true
        val wasListening = dictation.state.value.phase == DictationState.Phase.Listening
        dictation.stop()
        if (!wasListening) return false
        dictation.state.first { it.phase == DictationState.Phase.Idle }
        return true
    }
}

@Composable
internal fun rememberComposerDictation(remote: Remote, draftKey: String?): ComposerDictation {
    val context = LocalContext.current
    val dictation = remember(remote) { Dictation.of(remote, context) }
    val owner = remember { Any() }
    val state by dictation.state.collectAsState()
    // Leaving the thread, or its draft, takes unfinished words back out.
    DisposableEffect(draftKey) {
        onDispose { if (ComposerDictation(dictation, owner, dictation.state.value).active) dictation.cancel() }
    }
    return ComposerDictation(dictation, owner, state)
}

/** Starts dictating into [input], asking for the microphone first when it must; [instead] where that isn't [possible]. */
@Composable
internal fun rememberDictate(dictation: ComposerDictation, input: DictationInput, possible: Boolean, instead: () -> Unit): () -> Unit {
    val start = { dictation.dictation.start(input, dictation.owner) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }
    return {
        when {
            !possible -> runCatching(instead)
            dictation.dictation.hasPermission() -> start()
            else -> ask.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
