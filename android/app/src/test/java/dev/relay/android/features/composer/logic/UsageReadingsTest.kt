package dev.relay.android.features.composer.logic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

// A phone newer than its Relay host asks for limits the host doesn't know the agent has: it refuses the ask.
class UsageReadingsTest {
    private val week = 7 * 24 * 60 * 60 * 1000L
    private fun usage(provider: String) = ProviderUsage(provider, listOf(UsageWindow("weekly", 40.0, 1_800_000_000_000, week)))
    private val refused = IllegalStateException("Relay refused providerUsage")

    @Test fun showsAnAskAnOlderHostRefusedAsFailedAndReadsOnOnceItAnswers() {
        runBlocking {
            val readings = UsageReadings()
            val refusedAsk = readings.read("mini:opencode") { throw refused }
            assertNull(refusedAsk.usage)
            assertTrue(refusedAsk.failed)
            assertEquals(refusedAsk, readings["mini:opencode"])
            // Asking again once the host answers replaces the failure.
            val answered = readings.read("mini:opencode") { usage("opencode") }
            assertEquals(usage("opencode"), answered.usage)
            assertFalse(answered.failed)
            assertEquals(answered, readings["mini:opencode"])
        }
    }

    @Test fun keepsTheLimitsReadBeforeAFailureAndSaysItFailed() {
        runBlocking {
            val readings = UsageReadings()
            readings.read("mini:opencode") { usage("opencode") }
            val failed = readings.read("mini:opencode") { throw refused }
            assertEquals(usage("opencode"), failed.usage)
            assertTrue(failed.failed)
        }
    }

    @Test fun keepsAFailureToItsOwnComputerAndAgent() {
        runBlocking {
            val readings = UsageReadings()
            readings.read("mini:claude") { usage("claude") }
            readings.read("mini:opencode") { throw refused }
            readings.read("laptop:opencode") { usage("opencode") }
            assertFalse(readings["mini:claude"]!!.failed)
            assertTrue(readings["mini:opencode"]!!.failed)
            assertFalse(readings["laptop:opencode"]!!.failed)
            assertNull(readings["laptop:claude"])
        }
    }

    @Test fun doesNotCallACancelledAskAFailure() {
        runBlocking {
            val readings = UsageReadings()
            try {
                readings.read("mini:opencode") { throw CancellationException("left the screen") }
                fail("the cancellation should have passed through")
            } catch (_: CancellationException) {
            }
            assertNull(readings["mini:opencode"])
        }
    }
}
