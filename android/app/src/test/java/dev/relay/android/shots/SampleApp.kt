package dev.relay.android.shots

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import dev.relay.android.features.updates.Updates
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.Navigator
import dev.relay.android.lib.navigation.Route
import dev.relay.android.remote.OfflineCache
import dev.relay.android.remote.Remote
import dev.relay.android.remote.credentials.ComputerSeen
import dev.relay.android.remote.credentials.CredentialStore
import dev.relay.android.remote.credentials.Paired
import dev.relay.android.remote.credentials.SecretBox
import dev.relay.android.remote.model.RemoteCredentials
import dev.relay.android.remote.model.RemoteOverview
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.ToastState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

/**
 * The services a whole screen asks for, filled with sample data. The paired computers sit at an
 * address nothing answers, so a shot shows what's kept offline.
 */
class SampleApp(computers: List<String> = emptyList(), overview: RemoteOverview? = null, newest: String? = null) {
    val app: Application = ApplicationProvider.getApplicationContext()
    val scope = CoroutineScope(Dispatchers.Unconfined)
    val dir: File = Files.createTempDirectory("relay-shots").toFile()

    val remote = Remote(
        store = CredentialStore(File(dir, "computers"), PlainBox),
        cache = OfflineCache(File(dir, "cache")),
        seen = ComputerSeen(File(dir, "seen.json")),
        appVersion = "0.30.0",
        deviceName = "Sample phone",
        warn = { _, _ -> },
    )

    val updates: Updates

    init {
        keepLastLook(newest)
        updates = Updates(app, remote, scope)
        runBlocking {
            pairWith(computers, overview)
            updates.latest.check()
        }
    }

    private suspend fun pairWith(computers: List<String>, overview: RemoteOverview?) {
        val store = CredentialStore(File(dir, "computers"), PlainBox)
        val saved = computers.map { RemoteCredentials(listOf("192.0.2.10"), 4711, key = "key-$it", name = it, deviceId = "phone", token = "sample") }
        saved.forEach { store.saveCredentials(it) }
        store.savePaired(Paired(saved.map { it.key }, saved.firstOrNull()?.key))
        if (overview != null && saved.isNotEmpty()) OfflineCache(File(dir, "cache")).save(saved.first().key, overview)
        remote.load()
    }

    /** The last look at the release feed as the app keeps it, recent enough that it doesn't look again. */
    private fun keepLastLook(version: String?) {
        val now = System.currentTimeMillis()
        val newest = version?.let { """"newest":{"release":"$it","version":"$it","url":"https://example.com/relay.apk","size":31400000,"applicationId":"${app.packageName}"},""" }.orEmpty()
        app.getSharedPreferences("relay-updates", Context.MODE_PRIVATE).edit()
            .putString("relay.latestApp", """{$newest"checkedAt":$now,"triedAt":$now}""").commit()
    }
}

/** Seals nothing: the JVM has no Android Keystore, and the pairings are made up. */
object PlainBox : SecretBox {
    override fun seal(plain: ByteArray) = plain
    override fun open(sealed: ByteArray) = sealed
}

class SampleNavigator(override val canGoBack: Boolean = true) : Navigator {
    override val stack: List<Route> = listOf(Route.Home)
    override fun push(route: Route) = Unit
    override fun pop() = true
    override fun replace(route: Route) = Unit
    override fun openInPane(route: Route) = Unit
    override fun home() = Unit
}

@Composable
fun SampleShell(canGoBack: Boolean = true, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalNavigator provides SampleNavigator(canGoBack), LocalToasts provides ToastState(), content = content)
