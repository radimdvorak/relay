package dev.relay.android.features.dictation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.relay.android.remote.Remote
import dev.relay.android.ui.Haptic
import dev.relay.android.ui.rememberHaptics

/** Whether the computer can take dictation: desktops from before phone dictation don't say, and can't. */
@Composable
fun canDictate(remote: Remote): Boolean {
    val overview by remote.overview.collectAsState()
    val engines = DictationEngines.of(LocalContext.current)
    val picked by engines.picked.collectAsState()
    if (engines.available && picked == DictationEngine.Phone) return true
    val d = overview?.dictation
    return d != null && d != "unsupported"
}

/**
 * The composer's mic, as on the desktop: tap to start and stop, or hold to talk and let go to
 * finish. `owner` tells this composer's session from another's; `target` is where the words go;
 * [strong] makes it the composer's dark circle.
 */
@Composable
fun DictationButton(
    remote: Remote,
    owner: Any,
    target: () -> DictationTarget,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    strong: Boolean = false,
) {
    val context = LocalContext.current
    val dictation = remember(remote) { Dictation.of(remote, context) }
    val session by dictation.state.collectAsState()
    val haptics = rememberHaptics()
    val mine = session.owner === owner && session.phase != DictationState.Phase.Idle
    val listening = mine && session.phase == DictationState.Phase.Listening

    val currentTarget by rememberUpdatedState(target)
    val start = {
        haptics.perform(Haptic.Light)
        dictation.start(currentTarget(), owner)
    }
    // Android's prompt first; a refusal reaches Dictation, which says so.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }

    val levels = remember { mutableStateListOf<Float>().apply { repeat(MIC_BARS) { add(0f) } } }
    LaunchedEffect(listening) {
        for (i in 0 until MIC_BARS) levels[i] = 0f
        if (!listening) return@LaunchedEffect
        dictation.levels.collect { level ->
            levels.removeAt(0)
            levels.add(level)
        }
    }

    MicCircle(
        MicLook(strong = strong || mine, live = mine, loading = session.loading, enabled = enabled),
        levels = { levels },
        onPress = {
            val idle = dictation.state.value.phase == DictationState.Phase.Idle
            if (idle) {
                if (dictation.hasPermission()) start() else ask.launch(Manifest.permission.RECORD_AUDIO)
            }
            idle
        },
        onFinish = {
            haptics.perform(Haptic.Light)
            dictation.stop()
        },
        modifier = modifier,
    )
}
