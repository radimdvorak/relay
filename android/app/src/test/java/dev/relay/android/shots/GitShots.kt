package dev.relay.android.shots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.features.git.BranchesPage
import dev.relay.android.features.git.ChangesPage
import dev.relay.android.features.git.ChangesState
import dev.relay.android.features.git.CiRuns
import dev.relay.android.features.git.CommitBody
import dev.relay.android.features.git.CommitDraft
import dev.relay.android.features.git.CommitPage
import dev.relay.android.features.git.DiffPage
import dev.relay.android.features.git.HistoryPage
import dev.relay.android.features.git.PlanBody
import dev.relay.android.features.git.TurnPage
import dev.relay.android.remote.model.BranchList
import dev.relay.android.remote.model.BranchRef
import dev.relay.android.remote.model.CiCommit
import dev.relay.android.remote.model.CiRun
import dev.relay.android.remote.model.CiStatus
import dev.relay.android.remote.model.CommitDetail
import dev.relay.android.remote.model.CommitFileChange
import dev.relay.android.remote.model.CommitLog
import dev.relay.android.remote.model.CommitRef
import dev.relay.android.remote.model.CommitSummary
import dev.relay.android.remote.model.DiffHunk
import dev.relay.android.remote.model.DiffLine
import dev.relay.android.remote.model.GitCommit
import dev.relay.android.remote.model.LineCounts
import dev.relay.android.remote.model.MergePlan
import dev.relay.android.remote.model.MergeResult
import dev.relay.android.remote.model.RemoteDiff
import dev.relay.android.remote.model.TurnFileChange
import dev.relay.android.remote.model.WorkingChange
import dev.relay.android.remote.model.WorkingTree
import dev.relay.android.ui.RelaySheetFrame
import dev.relay.android.ui.theme.RelayTheme
import dev.relay.android.ui.theme.ThemeMode
import org.junit.Test

/** The Git pages: changes, the commit sheet, diffs, turns, branches, history, merging. */
class GitShots : ShotTest() {
    private val dir = "android/app/src/main/java/dev/relay/android/features"
    private val tree = WorkingTree(
        head = "abc", branch = "ax/lists", revision = "r",
        changes = listOf(
            WorkingChange("$dir/git/ChangesScreen.kt", index = "M", worktree = " "),
            WorkingChange("$dir/git/ChangesToolbar.kt", index = "A", worktree = " "),
            WorkingChange("$dir/pulls/PullScreen.kt", index = " ", worktree = "M"),
            WorkingChange("$dir/pulls/PullPageBody.kt", index = "?", worktree = "?"),
            WorkingChange("$dir/pulls/OldPullCard.kt", index = " ", worktree = "D"),
            WorkingChange("README.md", index = " ", worktree = "U", conflict = true),
            WorkingChange("$dir/git/AFileWithAVeryLongNameThatWontFitOnOneLineOfThePhone.kt", index = " ", worktree = "M"),
        ),
        upstream = "origin/ax/lists", ahead = 3, behind = 2, pushTarget = "origin/ax/lists", lines = LineCounts(846, 231),
        outgoing = listOf(GitCommit("1a2b3c4d5e", "feat(android): rework the git screens"), GitCommit("2b3c4d5e6f", "refactor(android): split the pull page so its parts can be drawn on their own")),
    )

    @Composable
    private fun Changes(state: ChangesState) =
        ChangesPage(state, {}, {}, {}, { _, _ -> }, {}, {}, {})

    @Test
    fun changes() {
        for (device in ShotDevice.entries) for (dark in listOf(false, true)) Shot("changes", device, dark) { Changes(ChangesState(tree)) }
        Shot("changes-relay-dark", act = { threeButtonBar() }) { InPalette("relay-dark") { Changes(ChangesState(tree)) } }
        Shot("changes-clean") { Changes(ChangesState(tree.copy(changes = emptyList(), behind = 0))) }
        Shot("changes-long") {
            LargeText {
                Changes(
                    ChangesState(
                        tree.copy(
                            branch = "feature/lists-rework/with-a-really-long-branch-name-from-a-ticket-1234", operation = "MERGE_HEAD", ahead = 128, behind = 256,
                            changes = (1..400).map { WorkingChange("$dir/many/File$it.kt", index = " ", worktree = "M") },
                        ),
                        busy = "ship",
                    ),
                )
            }
        }
        Shot("changes-failed", dark = true) { Changes(ChangesState(error = "Can't reach mac. The computer may be asleep.")) }
        Shot("changes-loading") { Changes(ChangesState()) }
    }

    @Composable
    private fun SheetOver(title: String, body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
        androidx.compose.foundation.layout.Box(Modifier) {
            Changes(ChangesState(tree))
            androidx.compose.foundation.layout.Box(Modifier.padding(8.dp)) {
                RelaySheetFrame(title, {}) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = body)
                }
            }
        }
    }

    @Test
    fun sheets() {
        val state = ChangesState(tree, draft = CommitDraft("feat(android): rework the git screens\n\nThe actions float over the list now."))
        val fine = tree.copy(changes = tree.changes.filter { !it.conflict })
        val plan = MergePlan(
            "ax/lists", "a", "android-expressive", "b", bases = listOf("android-expressive", "dev", "main"),
            commits = List(11) { GitCommit("abcdef$it", "refactor(android): step number $it of the lists rework") },
            pushTarget = "origin/android-expressive", uncommitted = 2, checkedOutAt = "/Users/me/relay-android-expressive",
        )
        val ci = CiStatus(
            branch = "ax/lists", commit = CiCommit("abcdef123", "feat(android): rework the git screens"), ahead = 2,
            runs = listOf(CiRun("build", "failure", "unit tests", "u", 0), CiRun("lint", "success", null, "u", 0), CiRun("e2e", "running", null, "u", 0)),
        )
        for (dark in listOf(false, true)) {
            Shot("commit-sheet", dark = dark) { SheetOver("Commit") { CommitBody(fine, state, {}, {}, { _, _ -> }) } }
            Shot("merge-sheet", dark = dark) { SheetOver("Merge branch") { PlanBody(plan, null, true, false, null, {}, {}, {}, {}) } }
            Shot("ci-sheet", dark = dark) { SheetOver("CI") { CiRuns(ci, System.currentTimeMillis()) {} } }
        }
        Shot("merge-conflict") {
            SheetOver("Merge branch") { PlanBody(plan.copy(bases = emptyList(), pushTarget = null), MergeResult(false, conflicts = listOf("$dir/git/Common.kt")), true, false, null, {}, {}, {}, {}) }
        }
    }

    private val diff = RemoteDiff(
        "$dir/git/DiffScreen.kt", binary = false, truncated = true,
        hunks = listOf(
            DiffHunk(
                "@@ -118,9 +118,11 @@ private fun HunkHeader(header: String)",
                listOf(
                    DiffLine("same", "@Composable", 118, 118),
                    DiffLine("same", "private fun lineTint(kind: String): Color = when (kind) {", 119, 119),
                    DiffLine("del", "    \"add\" -> RelayTheme.colors.diffAddition", 120),
                    DiffLine("del", "    \"del\" -> RelayTheme.colors.diffDeletion", 121),
                    DiffLine("add", "    \"add\" -> RelayTheme.colors.addedBg", new = 120),
                    DiffLine("add", "    \"del\" -> RelayTheme.colors.removedBg // the palette's own, so a desktop theme's diff colours carry over to the phone", new = 121),
                    DiffLine("add", "", new = 122),
                    DiffLine("same", "    else -> Color.Transparent", 122, 123),
                    DiffLine("same", "}", 123, 124),
                ),
            ),
            DiffHunk(
                "@@ -1204,4 +1206,3 @@",
                listOf(
                    DiffLine("same", "    val fine = true", 1204, 1206),
                    DiffLine("del", "    val gone = 42", 1205),
                    DiffLine("same", "    return fine", 1206, 1207),
                ),
            ),
        ),
    )

    @Composable
    private fun Diff(split: Boolean, wide: Boolean) =
        DiffPage(diff.path, diff, null, split, {}, {}, if (wide) ({}) else null)

    @Test
    fun diffs() {
        for (dark in listOf(false, true)) {
            Shot("diff", dark = dark) { Diff(split = false, wide = false) }
            Shot("diff", ShotDevice.FoldInner, dark) { Diff(split = false, wide = true) }
            Shot("diff-split", ShotDevice.FoldInner, dark) { Diff(split = true, wide = true) }
        }
        for (name in desktopPalettes.keys) Shot("diff-$name") { InPalette(name) { Diff(split = false, wide = false) } }
        Shot("diff-split-relay-dark", ShotDevice.FoldInner) { InPalette("relay-dark") { Diff(split = true, wide = true) } }
        Shot("diff-binary") { DiffPage("icon.png", diff.copy(binary = true), null, false, {}, {}, null) }
    }

    @Test
    fun turns() {
        val files = listOf(
            TurnFileChange("README.md", 4, 1),
            TurnFileChange("$dir/git/ChangesScreen.kt", 120, 88),
            TurnFileChange("$dir/git/ChangesToolbar.kt", 96, 0, revertedBy = "x"),
            TurnFileChange("$dir/pulls/icon.png", 0, 0, binary = true),
        )
        for (dark in listOf(false, true)) Shot("turn", dark = dark) {
            TurnPage("Claude · 14:32", files, "4 files changed · 1 rolled back", null, null, {}, {}, {}, {}, {}, {}, null, {})
        }
        Shot("turn-worktree", ShotDevice.FoldInner) {
            TurnPage("relay/lists-rework-with-a-long-branch-name-in-a-worktree", files, "4 files ahead of android-expressive", null, null, {}, {}, {}, null, {}, {}, "android-expressive-with-a-very-long-name", {})
        }
        Shot("turn-empty") { TurnPage("Claude · 14:32", emptyList(), "0 files changed", null, null, {}, {}, {}, {}, {}, {}, null, {}) }
    }

    @Test
    fun branches() {
        val list = BranchList(
            "ax/lists", "abc",
            listOf("ax/lists", "android-expressive", "dev", "main", "feature/lists-rework/with-a-really-long-branch-name-from-a-ticket-1234", "feature/lists-rework/with-a-really-long-branch-name-from-a-ticket-1235")
                .mapIndexed { i, n -> BranchRef(n, "refs/heads/$n", current = i == 0, worktree = i == 1) } +
                (1..300).map { BranchRef("origin/branch-$it", "refs/remotes/origin/branch-$it", remote = true) },
        )
        for (dark in listOf(false, true)) Shot("branches", dark = dark) { BranchesPage(list, null, false, null, {}, {}, { {} }, {}) }
        Shot("branches-large") { LargeText { BranchesPage(list, null, false, null, {}, {}, { {} }, {}) } }
    }

    @Test
    fun history() {
        val now = System.currentTimeMillis() / 1000
        val commits = List(30) {
            CommitSummary("abcdef$it", author = "Ada Lovelace", time = now - it * 5000L, subject = "feat(android): step $it, with a subject long enough to be cut somewhere near the end", refs = if (it == 0) listOf(CommitRef("main", "head"), CommitRef("origin/main", "remote")) else emptyList())
        }
        for (dark in listOf(false, true)) Shot("history", dark = dark) { HistoryPage(CommitLog(commits, true), null, {}, {}, {}, {}) }
        val commit = CommitDetail(
            "abcdef1234567890abcdef1234567890abcdef12", author = "Ada Lovelace", time = now,
            subject = "feat(android): rework the git screens", body = "The actions float over the list now, so committing is in reach however many files changed.",
            files = listOf(
                CommitFileChange("$dir/git/ChangesScreen.kt", null, "M", 120, 88),
                CommitFileChange("$dir/git/ChangesToolbar.kt", null, "A", 96, 0),
                CommitFileChange("$dir/git/CommitScreen.kt", "$dir/git/HistoryScreen.kt", "R", 12, 3),
                CommitFileChange("icon.png", null, "A", 0, 0, binary = true),
            ),
        )
        for (dark in listOf(false, true)) Shot("commit", dark = dark) { CommitPage(commit.sha, commit, null, {}, {}, {}) }
    }
}

/** The desktop's own themes (src/lib/builtin-themes.ts): the default dark, a warm light, a vivid dark. */
internal val desktopPalettes = mapOf(
    "relay-dark" to mapOf(
        "kind" to "dark", "sidebar" to "#232326", "surface" to "#1e1e21", "toolbar" to "#242427", "inbox" to "#28282c", "text" to "#e1e1e5",
        "muted" to "#94949f", "border" to "#37373d", "hover" to "#34343c", "selected" to "#41414f", "accent" to "#aaa8e5", "accentSoft" to "#32313f",
        "send" to "#aaa8e5", "onSend" to "#1b1b22", "diffAddition" to "#213f2d", "diffDeletion" to "#45272c",
    ),
    "solarized" to mapOf(
        "kind" to "light", "sidebar" to "#eee8d5", "surface" to "#fdf6e3", "toolbar" to "#f7f0dc", "inbox" to "#f5eedb", "text" to "#3c4c53",
        "muted" to "#556a71", "border" to "#e2dbc6", "hover" to "#e8e1cc", "selected" to "#e0d8c1", "accent" to "#268bd2", "accentSoft" to "#e3edea",
        "diffAddition" to "#e2edc6", "diffDeletion" to "#f6d9cf",
    ),
    "dracula" to mapOf(
        "kind" to "dark", "sidebar" to "#21222c", "surface" to "#282a36", "toolbar" to "#2b2d3a", "inbox" to "#2d2f3d", "text" to "#f8f8f2",
        "muted" to "#9aa3c9", "border" to "#3b3d4f", "hover" to "#363849", "selected" to "#44475a", "accent" to "#bd93f9", "accentSoft" to "#3d3a55",
        "diffAddition" to "#244a36", "diffDeletion" to "#57293a",
    ),
)

/** In Computer mode, wearing one of [desktopPalettes]. */
@Composable
internal fun InPalette(name: String, content: @Composable () -> Unit) = RelayTheme(ThemeMode.Computer, desktopPalettes.getValue(name), content)
