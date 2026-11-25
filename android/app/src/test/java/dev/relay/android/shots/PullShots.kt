package dev.relay.android.shots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import dev.relay.android.features.pulls.ListedPull
import dev.relay.android.features.pulls.PullActions
import dev.relay.android.features.pulls.PullPage
import dev.relay.android.features.pulls.PullPageBody
import dev.relay.android.features.pulls.PullSignals
import dev.relay.android.features.pulls.PullsBody
import dev.relay.android.features.pulls.PullsState
import dev.relay.android.features.pulls.Relation
import dev.relay.android.remote.model.CiCommit
import dev.relay.android.remote.model.CiRun
import dev.relay.android.remote.model.CiStatus
import dev.relay.android.remote.model.HostUser
import dev.relay.android.remote.model.IssueHit
import dev.relay.android.remote.model.IssueRepo
import dev.relay.android.remote.model.MergeReadiness
import dev.relay.android.remote.model.PullBranch
import dev.relay.android.remote.model.PullComment
import dev.relay.android.remote.model.PullDetail
import dev.relay.android.remote.model.PullFile
import dev.relay.android.remote.model.PullMerged
import dev.relay.android.remote.model.PullReview
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.ToastState
import org.junit.Test
import java.time.Instant

/** The pull requests tab and one pull request. */
class PullShots : ShotTest() {
    private val gh = "https://github.com"
    private fun ago(minutes: Long) = Instant.now().minusSeconds(minutes * 60).toString()

    private fun pull(
        number: Int, title: String, relation: Relation, minutes: Long, state: String = "open",
        draft: Boolean = false, merged: Boolean = false, comments: Int = 0, who: String = "ada",
    ) = ListedPull(
        IssueHit(number, title, state, updated_at = ago(minutes), user = HostUser(who, 1), repository = IssueRepo("relay", "relaycodehq"), comments = comments, pull_request = PullMerged(merged), draft = draft),
        gh, relation,
    )

    private val pulls = listOf(
        pull(41, "Sort threads by what needs you", Relation.Review, 12, comments = 3, who = "grace"),
        pull(40, "Let a paired computer's preview reach the phone even when the tunnel was opened from another thread", Relation.Assigned, 90, who = "linus"),
        pull(39, "Rework the Android app", Relation.Mine, 4),
        pull(38, "Stop the composer jumping on rotate", Relation.Mine, 60),
        pull(37, "Bump Compose to 1.13", Relation.Mine, 60 * 20),
        pull(36, "Try a wavy progress bar", Relation.Mine, 60 * 80, draft = true),
        pull(35, "Old experiment", Relation.Mine, 60 * 24 * 20, state = "closed"),
        pull(34, "Quick switch presets", Relation.Mine, 60 * 24 * 30, state = "closed", merged = true),
    )
    private val signals = listOf(
        PullSignals("success", null, "android-expressive", 846, 231),
        PullSignals("failure", "changes", "fix/composer-jump-when-the-phone-rotates-and-the-keyboard-is-up", 46, 31),
        PullSignals("success", "approved", "build/compose", 12, 12),
        PullSignals("running", null, "wavy", 3, 0),
    ).mapIndexed { i, s -> pulls[2 + i].key to s }.toMap()

    @Composable
    private fun Pulls(state: PullsState, query: String = "") {
        SampleTabPage("Pull requests") {
            PullsBody(state, query, selected = null, bottom = 96.dp, refreshing = false, {}, {}, {}, {})
        }
    }

    @Test
    fun list() {
        for (device in ShotDevice.entries) for (dark in listOf(false, true)) Shot("pulls", device, dark) { Pulls(PullsState(pulls, signals = signals, show = "all")) }
        Shot("pulls-relay-dark") { InPalette("relay-dark") { Pulls(PullsState(pulls, signals = signals, show = "all")) } }
        Shot("pulls-large") { LargeText { Pulls(PullsState(pulls, signals = signals, pending = setOf("https://git.example"), failed = mapOf("https://dev.azure.com/x" to "The sign-in expired; run az login again on your computer"))) } }
        Shot("pulls-empty") { Pulls(PullsState(emptyList())) }
        Shot("pulls-signed-out", dark = true) { Pulls(PullsState(emptyList(), signedOut = true)) }
        Shot("pulls-loading") { Pulls(PullsState(null)) }
        Shot("pulls-failed") { Pulls(PullsState(null, error = "Not connected.")) }
    }

    private val detail = PullDetail(
        "relaycodehq", "relay", 38, title = "Stop the composer jumping on rotate", state = "open", html_url = "u",
        body = "Rotating with the keyboard up made the composer land a keyboard's height too high.\n\n## Why\n\n- the inset was read **twice**\n- `imePadding()` sat inside and outside\n\nChecked on a Fold7.",
        head = PullBranch("fix/composer-jump-when-the-phone-rotates", "abc"), base = PullBranch("dev", "def"),
        user = HostUser("ada", 1), additions = 46, deletions = 31, changed_files = 3, updated_at = ago(60),
    )
    private val ci = CiStatus(
        branch = "fix", commit = CiCommit("abcdef1"),
        runs = listOf(CiRun("build", "failure", "unit tests", "u", 0), CiRun("lint", "success", null, "u", 0), CiRun("e2e", "running", null, "u", 0)),
    )
    private val page = PullPage(
        detail, ci,
        reviews = listOf(PullReview(1, "The inset is still read twice on a Fold.", "REQUEST_CHANGES", HostUser("grace", 2), ago(30))),
        files = listOf(
            PullFile("android/app/src/main/java/dev/relay/android/features/composer/Composer.kt", additions = 40, deletions = 31),
            PullFile("README.md", additions = 6),
        ),
        moreFiles = true,
        comments = listOf(PullComment(1, "Does it still happen with a **hardware** keyboard?", HostUser("linus", 3), ago(20))),
        readiness = MergeReadiness("clean", 2, listOf("squash", "merge")), merges = true,
    )
    private val actions = PullActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Composable
    private fun Pull(page: PullPage?, project: String? = "relay", threads: Int = 2) {
        CompositionLocalProvider(LocalToasts provides ToastState()) {
            PullPageBody(
                "relay #38", page, null, null, project,
                List(threads) { sampleThread("t$it", "relay", "Fix the composer jump, try $it", 5L + it, running = it == 0, branch = detail.head.ref) },
                refreshing = false, actions,
            )
        }
    }

    @Test
    fun detail() {
        for (device in ShotDevice.entries) for (dark in listOf(false, true)) Shot("pull", device, dark) { Pull(page) }
        Shot("pull-unfolded", act = {
            tap("1 check failing")
            compose.mainClock.advanceTimeBy(400)
            tap("Changes requested")
        }) { Pull(page) }
        Shot("pull-relay-dark", act = { threeButtonBar() }) { InPalette("relay-dark") { Pull(page) } }
        Shot("pull-conflicts") {
            LargeText { Pull(page.copy(readiness = MergeReadiness("conflicts", 14, listOf("merge")), reviews = emptyList()), project = null, threads = 0) }
        }
        Shot("pull-merged", dark = true) { Pull(page.copy(pull = detail.copy(merged = true, state = "closed", body = ""), comments = emptyList(), ci = null)) }
    }

    /** Through semantics, so a still clock doesn't hold the press back. */
    private fun tap(text: String) {
        val click = compose.onNode(hasText(text)).fetchSemanticsNode().config[SemanticsActions.OnClick]
        compose.runOnUiThread { click.action?.invoke() }
    }
}
