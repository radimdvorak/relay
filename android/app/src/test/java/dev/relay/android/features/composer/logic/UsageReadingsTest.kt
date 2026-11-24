package dev.relay.android.features.composer.logic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

// A phone newer than its Relay host asks for limits the host doesn't know the agent has: it refuses the ask.
class UsageReadingsTest {
    private val week = 7 * 24 * 60 * 60 * 1000L
    private val refused = IllegalStateException("Relay refused providerUsage")
    private val mini = UsageConnection("mini", Any())
    private val laptop = UsageConnection("laptop", Any())

    private fun usage(provider: String = "opencode", percent: Double = 40.0) =
        ProviderUsage(provider, listOf(UsageWindow("weekly", percent, 1_800_000_000_000, week)))

    private suspend fun UsageReadings.readFor(
        connection: UsageConnection<Any> = mini,
        provider: String = "opencode",
        ask: suspend () -> ProviderUsage,
    ) = checkNotNull(read(connection, provider, { true }) { ask() })

    @Test fun showsAnAskAnOlderHostRefusedAsFailedAndReadsOnOnceItAnswers() {
        runBlocking {
            val readings = UsageReadings()
            val refusedAsk = readings.readFor { throw refused }
            assertNull(refusedAsk.usage)
            assertTrue(refusedAsk.failed)
            assertTrue(refusedAsk.unavailable)
            assertEquals(refusedAsk, readings["mini", "opencode"])
            // Asking again once the host answers replaces the failure.
            val answered = readings.readFor { usage() }
            assertEquals(usage("opencode"), answered.usage)
            assertFalse(answered.failed)
            assertEquals(answered, readings["mini", "opencode"])
        }
    }

    @Test fun keepsTheLimitsReadBeforeAFailureAndSaysItFailed() {
        runBlocking {
            val readings = UsageReadings()
            readings.readFor { usage() }
            val failed = readings.readFor { throw refused }
            assertEquals(usage("opencode"), failed.usage)
            assertTrue(failed.failed)
            assertFalse(failed.unavailable)
        }
    }

    @Test fun aFailureReplacesAnEmptyStatusUntilTheNextSuccess() {
        runBlocking {
            val readings = UsageReadings()
            val empty = ProviderUsage("opencode", message = "Shows while OpenCode is running")
            assertFalse(readings.readFor { usage() }.unavailable)
            val waiting = readings.readFor { empty }
            assertEquals(empty, waiting.usage)
            assertFalse(waiting.unavailable)
            val failed = readings.readFor { throw refused }
            assertTrue(failed.unavailable)
            assertTrue(failed.usage?.windows.isNullOrEmpty())
            val recovered = readings.readFor { usage(percent = 65.0) }
            assertFalse(recovered.unavailable)
            assertEquals(usage(percent = 65.0), recovered.usage)
        }
    }

    @Test fun keepsAFailureToItsOwnComputerAndAgent() {
        runBlocking {
            val readings = UsageReadings()
            readings.readFor(provider = "claude") { usage("claude") }
            readings.readFor { throw refused }
            readings.readFor(laptop) { usage() }
            assertFalse(readings["mini", "claude"]!!.failed)
            assertTrue(readings["mini", "opencode"]!!.failed)
            assertFalse(readings["laptop", "opencode"]!!.failed)
            assertNull(readings["laptop", "claude"])
        }
    }

    @Test fun doesNotCallACancelledAskAFailure() {
        runBlocking {
            val readings = UsageReadings()
            try {
                readings.readFor { throw CancellationException("left the screen") }
                fail("the cancellation should have passed through")
            } catch (_: CancellationException) {
            }
            assertNull(readings["mini", "opencode"])
        }
    }

    @Test fun keepsLateAnswersAndRetriesWithTheirOwnComputer() {
        runBlocking {
            val readings = UsageReadings()
            val previous = usage(percent = 10.0)
            readings.readFor { previous }
            var computer = mini.computer
            var client = mini.client
            fun current(source: UsageConnection<Any>) = source.matches(computer, client)
            val replyA = CompletableDeferred<ProviderUsage>()
            val replyB = CompletableDeferred<ProviderUsage>()

            val askA = async(start = CoroutineStart.UNDISPATCHED) {
                readings.read(mini, "opencode", ::current) {
                    assertSame(mini.client, it)
                    replyA.await()
                }
            }
            computer = laptop.computer
            var askedOldClient = false
            assertNull(readings.read(mini, "opencode", ::current) {
                askedOldClient = true
                usage()
            })
            assertFalse(askedOldClient)
            client = laptop.client
            val askB = async(start = CoroutineStart.UNDISPATCHED) {
                readings.read(laptop, "opencode", ::current) {
                    assertSame(laptop.client, it)
                    replyB.await()
                }
            }

            replyA.completeExceptionally(refused)
            assertNull(askA.await())
            assertEquals(UsageReading(previous), readings["mini", "opencode"])
            assertNull(readings["laptop", "opencode"])
            replyB.completeExceptionally(refused)
            assertEquals(UsageReading(failed = true), askB.await())
            val newerB = usage(percent = 75.0)
            assertEquals(UsageReading(newerB), readings.read(laptop, "opencode", ::current) { newerB })

            val reconnected = UsageConnection(mini.computer, Any())
            computer = reconnected.computer
            client = reconnected.client
            val failedA = readings.read(reconnected, "opencode", ::current) { throw refused }
            assertEquals(UsageReading(previous, failed = true), failedA)
            val newerA = usage(percent = 20.0)
            assertEquals(UsageReading(newerA), readings.read(reconnected, "opencode", ::current) { newerA })
            assertEquals(UsageReading(newerB), readings["laptop", "opencode"])
        }
    }

    @Test fun ignoresAnOlderReadAfterARetryHasAnswered() {
        runBlocking {
            val readings = UsageReadings()
            val late = CompletableDeferred<ProviderUsage>()
            val before = async(start = CoroutineStart.UNDISPATCHED) {
                readings.read(mini, "opencode", { true }) { late.await() }
            }
            val fresh = readings.readFor { usage() }
            late.complete(usage(percent = 15.0))
            assertNull(before.await())
            assertEquals(fresh, readings["mini", "opencode"])
        }
    }

    @Test fun ignoresAnAnswerThatArrivesAfterCancellation() {
        runBlocking {
            val readings = UsageReadings()
            val late = CompletableDeferred<ProviderUsage>()
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                readings.read(mini, "opencode", { true }) { withContext(NonCancellable) { late.await() } }
            }
            job.cancel()
            late.complete(usage())
            job.join()
            assertNull(readings["mini", "opencode"])
        }
    }
}
