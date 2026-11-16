package dev.relay.android.features.push

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.relay.android.features.notifications.WatchService
import dev.relay.android.lib.navigation.threadLink

/** Takes a computer's push (shared/push.ts) and shows it as a thread's notification. */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val chatId = data["chatId"] ?: return
        val computer = data["computer"] ?: return
        val push = (application as? HasPush)?.push ?: return
        val onScreen = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (!push.shows(computer, onScreen)) return
        // One sent before titles were hidden there.
        val title = data["title"].takeUnless { push.hidesTitles(computer) }
        val notice = pushNotice(data["kind"] ?: return, title) ?: return
        WatchService.post(this, chatId, notice.title, notice.body, threadLink(chatId, computer))
    }

    override fun onNewToken(token: String) {
        Push.tokens.value = token
    }
}

data class PushNotice(val title: String, val body: String)

/** A push's notification: the thread's title and how it ended, or only the latter when titles are hidden. */
fun pushNotice(kind: String, title: String?): PushNotice? {
    val ended = when (kind) {
        "finished" -> "Finished"
        "failed" -> "Failed"
        "waiting" -> "Needs you"
        else -> return null
    }
    if (title != null) return PushNotice(title, ended)
    val hidden = when (kind) {
        "finished" -> "A thread finished"
        "failed" -> "A thread failed"
        else -> "A thread needs you"
    }
    return PushNotice(hidden, "Open Relay to see which.")
}
