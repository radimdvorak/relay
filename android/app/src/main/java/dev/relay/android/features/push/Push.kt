package dev.relay.android.features.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import dev.relay.android.BuildConfig
import dev.relay.android.remote.Bridge
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.LinkState
import dev.relay.android.remote.client.setPush
import dev.relay.android.remote.model.PushRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Notifications through Firebase (shared/push.ts): the phone hands its token
 * to the computer it's connected to, which then tells it of a thread's news
 * while the app is away, so the app needn't stay connected for that.
 */
class Push(private val app: Context, private val remote: Remote, private val scope: CoroutineScope) {
    private val prefs = app.getSharedPreferences("relay-push", Context.MODE_PRIVATE)

    /** This build has Relay's Firebase app; builds without it stay connected instead. */
    val available = startFirebase(app)

    private val _hideTitles = MutableStateFlow(prefs.getBoolean(HIDE_TITLES_KEY, false))
    /** Pushes say only that a thread finished or needs you, not which. */
    val hideTitles: StateFlow<Boolean> = _hideTitles.asStateFlow()

    private val _delivering = MutableStateFlow(false)
    /** The active computer pushes this phone its news, so nothing needs to stay connected. */
    val delivering: StateFlow<Boolean> = _delivering.asStateFlow()

    /** A computer took this phone's registration, so the link may close again with the app away. */
    var registered: () -> Unit = {}

    fun setHideTitles(on: Boolean) {
        _hideTitles.value = on
        prefs.edit().putBoolean(HIDE_TITLES_KEY, on).apply()
    }

    /** Registers with each computer the phone connects to, while [notificationsOn]. */
    fun start(notificationsOn: StateFlow<Boolean>) {
        if (!available) return
        scope.launch { remote.active.collect { _delivering.value = it != null && prefs.getBoolean(pushedKey(it), false) } }
        scope.launch { tokens.value = tokens.value ?: currentToken() }
        // A token Firebase rotated with the link parked: open it to hand the new one over.
        scope.launch {
            tokens.filterNotNull().distinctUntilChanged().collect { if (_delivering.value) remote.unpark() }
        }
        // A computer that went back to a Relay without pushes no longer sends them.
        scope.launch {
            remote.fresh.collect { fresh -> if ((fresh.overview.bridge ?: 0) < Bridge.PUSH) remember(fresh.computer, false) }
        }
        scope.launch {
            combine(remote.status, remote.client, remote.overview, notificationsOn, _hideTitles) { status, client, overview, on, hide ->
                val online = status.state == LinkState.Online && client != null
                val takesPush = (overview?.bridge ?: 0) >= Bridge.PUSH
                if (online && takesPush) Wanted(on, hide) else null
            }.combine(tokens) { wanted, token -> wanted?.copy(token = token) }
                .distinctUntilChanged()
                .collect { wanted ->
                    if (wanted == null) return@collect
                    // Asked for already; the token comes back in a moment.
                    val waitingForToken = wanted.on && wanted.token == null
                    if (!waitingForToken) register(wanted)
                }
        }
    }

    private data class Wanted(val on: Boolean, val hideTitles: Boolean, val token: String? = null)

    private suspend fun register(wanted: Wanted) {
        val client = remote.client.value ?: return
        val computer = remote.active.value ?: return
        val token = wanted.token
        val registration = if (wanted.on && token != null) PushRegistration(token, computer, wanted.hideTitles) else null
        try {
            client.setPush(registration)
            remember(computer, registration != null)
            if (registration != null) registered()
        } catch (e: Exception) {
            // Not known to push this phone, so it stays connected to hear the news.
            Log.w(TAG, "Couldn't register for pushes", e)
            remember(computer, false)
        }
    }

    private fun remember(computer: String, pushed: Boolean) {
        prefs.edit().putBoolean(pushedKey(computer), pushed).apply()
        if (computer == remote.active.value) _delivering.value = pushed
    }

    private suspend fun currentToken(): String? = suspendCoroutine { done ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) Log.w(TAG, "No Firebase token", task.exception)
            done.resume(if (task.isSuccessful) task.result else null)
        }
    }

    companion object {
        private const val TAG = "Push"
        private const val HIDE_TITLES_KEY = "hide-titles"
        private fun pushedKey(computer: String) = "pushed:$computer"

        /** The phone's Firebase token, renewed by PushService when Firebase rotates it. */
        internal val tokens = MutableStateFlow<String?>(null)

        private fun startFirebase(context: Context): Boolean {
            if (BuildConfig.FIREBASE_APP_ID.isEmpty()) return false
            if (FirebaseApp.getApps(context).isNotEmpty()) return true
            val options = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
            return runCatching { FirebaseApp.initializeApp(context, options) }
                .onFailure { Log.w(TAG, "Firebase didn't start", it) }
                .isSuccess
        }
    }
}
