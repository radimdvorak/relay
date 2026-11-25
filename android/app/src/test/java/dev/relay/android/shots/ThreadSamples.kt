package dev.relay.android.shots

import dev.relay.android.remote.model.AgentActivity
import dev.relay.android.remote.model.AgentQuestion
import dev.relay.android.remote.model.AgentRequest
import dev.relay.android.remote.model.AgentTrace
import dev.relay.android.remote.model.AskedQuestion
import dev.relay.android.remote.model.AsyncAgentQuestions
import dev.relay.android.remote.model.CallAction
import dev.relay.android.remote.model.ChatMessage
import dev.relay.android.remote.model.QuestionOption
import dev.relay.android.remote.model.SecretRequest
import dev.relay.android.remote.model.TurnFileChange
import dev.relay.android.remote.model.TurnModel
import dev.relay.android.remote.model.TurnPreview
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Made-up messages for the thread's shots. */
object ThreadSamples {
    val now = System.currentTimeMillis()
    private const val MINUTE = 60_000L

    fun user(id: String, body: String, minutesAgo: Long = 12, side: Boolean? = null, pending: Boolean? = null, unread: Boolean? = null) =
        ChatMessage(id, "user", body, "complete", now - minutesAgo * MINUTE, "claude", 1, side = side, pending = pending, unread = unread)

    fun answer(
        id: String, body: String, status: String = "complete", minutesAgo: Long = 10, provider: String = "claude",
        trace: List<AgentTrace>? = null, changes: List<TurnFileChange>? = null, error: String? = null,
        questions: List<AsyncAgentQuestions>? = null, preview: TurnPreview? = null, signIn: String? = null,
    ) = ChatMessage(
        id, "assistant", body, status, now - minutesAgo * MINUTE, provider, 1, error = error, cost = 0.12,
        streamed = buildJsonObject { put("output", 1240); put("ms", 14_700) }, model = TurnModel("Opus 5.5"),
        trace = trace, questions = questions, preview = preview, changes = changes, signIn = signIn,
        ended = if (status == "streaming") null else now - (minutesAgo - 3) * MINUTE,
    )

    fun call(id: String, kind: String, label: String, status: String = "complete", detail: String? = null, vararg actions: CallAction) =
        AgentTrace("activity", id, activity = AgentActivity(id, kind, label, status, detail, actions = actions.toList().ifEmpty { null }))

    fun said(id: String, text: String) = AgentTrace("commentary", id, text)

    val trace = listOf(
        said("c1", "I'll look at how the composer measures itself before touching anything."),
        call("a1", "read", "Read Composer.kt", actions = arrayOf(CallAction("read", file = "features/composer/Composer.kt", lines = "40–120"))),
        call("a2", "search", "Search", actions = arrayOf(CallAction("search", pattern = "rememberImePadding", where = "features"))),
        call("a3", "command", "./gradlew test", status = "failed", detail = "> Task :app:test FAILED\n2 tests failed", actions = arrayOf(CallAction("test", target = "features/composer/ComposerTest.kt", failed = true))),
        said("c2", "The test pins the old inset, so it has to move with the fix."),
        call("a4", "file", "Edit", actions = arrayOf(CallAction("edit", file = "features/composer/Composer.kt"))),
        call("a5", "file", "Edit", actions = arrayOf(CallAction("edit", file = "features/composer/ComposerTest.kt"))),
        call("a6", "command", "./gradlew test", actions = arrayOf(CallAction("test", target = "features/composer/ComposerTest.kt"))),
        call("a7", "command", "git commit", actions = arrayOf(CallAction("commit", hash = "a41bb7c2d", subject = "fix(android): read the inset once"))),
    )

    val liveTrace = listOf(
        said("c1", "Reading the composer first."),
        call("a1", "read", "Read Composer.kt", actions = arrayOf(CallAction("read", file = "features/composer/Composer.kt"))),
        call("a2", "search", "Search", actions = arrayOf(CallAction("search", pattern = "imePadding", where = "features/composer"))),
        call("a3", "command", "./gradlew :app:compileDebugKotlin", status = "running", actions = arrayOf(CallAction("run", what = "./gradlew :app:compileDebugKotlin"))),
    )

    val changes = listOf(
        TurnFileChange("android/app/src/main/java/dev/relay/android/features/composer/Composer.kt", 46, 31),
        TurnFileChange("android/app/src/test/java/ComposerTest.kt", 8, 2),
    )

    const val MENU = """A cozy dinner is all about warmth and ease, so here's a simple plan you can pull off tonight.

## A relaxed menu

- **Start simple:** Set out warm bread, olives, and a soft cheese so guests can nibble while you finish up.
- **One-pot main:** A hearty beef or bean stew simmers mostly hands-off and fills the kitchen with a wonderful aroma.
- **Bright side:** Toss a crisp green salad with lemon, olive oil, and toasted nuts.
- **Easy dessert:** Bake apples with cinnamon and serve with a scoop of vanilla ice cream.

Dim the lights, light a few candles, and enjoy your evening!"""

    const val MARKDOWN = """The inset was read **twice**: once by `Scaffold` and once by the composer itself, see [Composer.kt](https://example.com).

# First heading
### Third heading

1. Read it once, in the screen
2. Pass it down as a plain `Dp`
   - nested item, with `code`
   - another
10. A two digit number

- [x] Pin the behaviour in a test
- [ ] Check it on the Fold

> Quoted: the keyboard's own animation already eases, so nothing here should animate a second time.

| File | Lines | What changed |
|---|---|---|
| `Composer.kt` | 46 | Takes the inset as a parameter instead of reading it |
| `Home.kt` | 3 | Passes it |

```kotlin
@Composable
fun Composer(bottom: Dp, modifier: Modifier = Modifier) {
    // One reader, so a rotation can't apply it twice.
    Column(modifier.padding(bottom = bottom)) {
        val shown = remember { mutableStateOf(true) }
    }
}
```

---

Accent is `#AAA8E5` and the surface `#1E1E21`."""

    val approval = AgentRequest(
        "r1", "approval", "Run a command outside the sandbox", detail = "git push --force-with-lease origin android-expressive",
        note = "Claude wants to update the branch on the server.",
        decisions = listOf("accept", "acceptForSession", "decline", "cancel").map { JsonPrimitive(it) },
    )

    val questions = listOf(
        AgentQuestion(
            "q1", "Where", "Which of the two should own the inset?",
            options = listOf(
                QuestionOption("The screen", "Every screen passes it down; one place to look."),
                QuestionOption("The composer", "Keeps callers short, and rotates badly."),
            ),
        ),
    )
    val question = AgentRequest("r2", "question", "Claude has a question", questions = questions)
    val secret = AsyncAgentQuestions("s1", secret = SecretRequest("npm publish token", "To publish 1.2.0. You find it under npmjs.com, Access Tokens."), questions = emptyList())
    val asked = AsyncAgentQuestions("s2", questions = questions)
    val answered = call("q9", "tool", "AskUserQuestion").let {
        it.copy(activity = it.activity!!.copy(questionAnswers = listOf(AskedQuestion("Which one?", "The screen"))))
    }
    val json = Json
}
