package dev.relay.android.features.notifications

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Android kills an app whose foreground service is stopped before it came to the foreground. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class WatchServiceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun arrive() = Robolectric.buildService(WatchService::class.java, shadowOf(app).nextStartedService).create().startCommand(0, 1).get()

    @Test
    fun stoppedBeforeItArrivesItComesToTheForegroundAndThenLeaves() {
        WatchService.start(app, "Relay", "")
        WatchService.stop(app)

        assertNull(shadowOf(app).nextStoppedService)
        val service = arrive()
        assertNotNull(shadowOf(service).lastForegroundNotification)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun wantedAgainBeforeItArrivesItStays() {
        WatchService.start(app, "Relay", "")
        WatchService.stop(app)
        WatchService.start(app, "Relay", "")

        assertFalse(shadowOf(arrive()).isStoppedBySelf)
        WatchService.stop(app)
        assertNotNull(shadowOf(app).nextStoppedService)
    }
}
