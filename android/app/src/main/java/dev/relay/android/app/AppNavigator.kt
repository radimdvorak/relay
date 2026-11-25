package dev.relay.android.app

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.relay.android.lib.navigation.Navigator
import dev.relay.android.lib.navigation.Route

/** The Navigator over Navigation 3's back stack, which always starts with Home. */
class AppNavigator(
    private val backStack: NavBackStack<NavKey>,
    private val isWide: () -> Boolean,
) : Navigator {
    override val stack: List<Route> get() = backStack.filterIsInstance<Route>()

    override val canGoBack: Boolean get() = backStack.size > 1

    /** Whether the last change swapped one destination for another instead of going deeper, so it fades. */
    var swapped = false
        private set

    override fun push(route: Route) {
        swapped = false
        if (backStack.lastOrNull() != route) backStack.add(route)
    }

    override fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    override fun replace(route: Route) {
        swapped = false
        if (backStack.size <= 1) backStack.add(route) else backStack[backStack.lastIndex] = route
    }

    override fun openInPane(route: Route) {
        if (route == Route.Home) return home()
        if (!isWide()) return push(route)
        home()
        backStack.add(route)
        swapped = true
    }

    override fun home() {
        swapped = true
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (backStack.firstOrNull() != Route.Home) {
            backStack.clear()
            backStack.add(Route.Home)
        }
    }

    /** A deep link: a pairing stacks over what's there, a thread opens as a destination. */
    fun open(route: Route) = when (route) {
        is Route.Pair -> push(route)
        else -> openInPane(route)
    }
}
