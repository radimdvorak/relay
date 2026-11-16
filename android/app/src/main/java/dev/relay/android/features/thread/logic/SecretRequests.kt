package dev.relay.android.features.thread.logic

// shared/secret-request.ts: what a thread keeps of a secret an agent asked
// for (request_secret). Never the value, only how the request ended.

/** The one hidden question a secret request carries, under which its outcome is kept. */
const val SECRET_QUESTION_ID = "secret"

/** How a secret request ended: the row's label, and where the value went under it. */
data class SecretOutcome(val label: String, val note: String? = null)

/** [kept] is what the group's answers hold under [SECRET_QUESTION_ID]; null when the user declined. */
fun secretOutcome(name: String, kept: String?): SecretOutcome = when (kept) {
    null -> SecretOutcome("Declined \"$name\"")
    "Remembered" -> SecretOutcome("Used saved \"$name\"", "Saved for this project with Always, so there was no card")
    "Always" -> SecretOutcome(
        "Saved \"$name\"",
        "In a private file, not in the chat. Always: this project's agents get it without asking",
    )
    else -> SecretOutcome("Saved \"$name\"", "In a private file, not in the chat")
}

/** Where the card says the value goes. */
fun secretDestination(agent: String) =
    "It goes to a private file that $agent's commands read, not into the chat. Relay deletes the file after 24 hours."
