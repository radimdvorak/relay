package dev.relay.android.lib.navigation

/** Shared-bounds key of the empty Home's greeting and the new-thread screen's, so it stays put between them. */
const val NEW_THREAD_GREETING = "new-thread-greeting"

/** How a new thread's composer opens: ready to type, on the Attach sheet, or listening. */
enum class ComposerStart { Type, Attach, Dictate }

/**
 * Home's composer leaves here what its plus or mic asked for, and the new-thread screen it opens
 * takes it. Not part of the route: a screen restored later shouldn't start the microphone again.
 */
object NextComposerStart {
    private var asked = ComposerStart.Type

    fun ask(start: ComposerStart) {
        asked = start
    }

    fun take(): ComposerStart = asked.also { asked = ComposerStart.Type }
}
