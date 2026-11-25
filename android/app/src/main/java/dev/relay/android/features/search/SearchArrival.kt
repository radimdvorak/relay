package dev.relay.android.features.search

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.Route
import dev.relay.android.ui.GROWTH_STIFFNESS
import dev.relay.android.ui.LocalScreenAnimation
import dev.relay.android.ui.LocalSharedTransitions
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.delay

private const val RESULTS_IN_MS = 160
private const val RESULTS_OUT_MS = 90

/** Home's list is covered by then, so no text shows through text, and the field is nearly where it's going. */
private const val RESULTS_WAIT_MS = 200L

/** What the gap under Home's title adds to its line. */
private val TITLE_GAP = 14.dp

/** How search's parts come in around its field, which Home's search button grows into. */
internal class SearchArrival(
    val sheet: Modifier = Modifier,
    val results: Modifier = Modifier,
    val resultsShown: Boolean = true,
    val resultsIn: EnterTransition = EnterTransition.None,
)

/**
 * The sheet comes up over the line Home's title stood on. The results are built once the field has
 * grown: a screenful of rows takes long enough to stall it on the way. [returning] from a result, and leaving for one, they move with the page instead.
 */
@Composable
internal fun rememberSearchArrival(route: Route.Search, returning: Boolean): SearchArrival {
    val screen = LocalScreenAnimation.current ?: return SearchArrival()
    val stack = LocalNavigator.current.stack
    val coveredByNext = route in stack && stack.last() != route
    val growsInPlace = LocalSharedTransitions.current != null
    val titleRoom = with(LocalDensity.current) { (RelayTheme.type.title.lineHeight.toDp() + TITLE_GAP).roundToPx() }

    var waiting by remember { mutableStateOf(!returning) }
    LaunchedEffect(Unit) {
        delay(RESULTS_WAIT_MS)
        waiting = false
    }

    val rise = spring(stiffness = GROWTH_STIFFNESS, visibilityThreshold = IntOffset.VisibilityThreshold)
    val arrives = !returning
    val leaves = !coveredByNext
    val sheetIn = if (arrives && growsInPlace) slideInVertically(rise) { titleRoom } else EnterTransition.None
    val sheetOut = if (leaves && growsInPlace) slideOutVertically(rise) { titleRoom } else ExitTransition.None
    val resultsOut = if (leaves) fadeOut(tween(RESULTS_OUT_MS)) else ExitTransition.None
    return with(screen) {
        SearchArrival(
            sheet = Modifier.animateEnterExit(sheetIn, sheetOut),
            results = Modifier.animateEnterExit(EnterTransition.None, resultsOut),
            resultsShown = !waiting,
            resultsIn = fadeIn(tween(RESULTS_IN_MS)),
        )
    }
}
