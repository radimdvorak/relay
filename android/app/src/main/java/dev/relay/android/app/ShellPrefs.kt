package dev.relay.android.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import dev.relay.android.ui.theme.DEFAULT_CONTRAST
import dev.relay.android.ui.theme.ThemeMode
import dev.relay.android.ui.theme.ThemeSettings

/**
 * The shell's own settings: theme, contrast and the sidebar. SharedPreferences rather
 * than DataStore because the first frame needs them synchronously; a flash of
 * the wrong theme or a sidebar jumping width is worse than a tiny disk read.
 */
class ShellPrefs(context: Context) : ThemeSettings {
    private val prefs = context.getSharedPreferences("relay-shell", Context.MODE_PRIVATE)

    override var themeMode by mutableStateOf(
        prefs.getString(THEME, null)?.let { name -> ThemeMode.entries.find { it.name == name } } ?: ThemeMode.Computer,
    )
        private set

    override fun setTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.edit { putString(THEME, mode.name) }
    }

    private val contrastState = mutableIntStateOf(prefs.getInt(CONTRAST, DEFAULT_CONTRAST))

    override var contrast: Int
        get() = contrastState.intValue
        set(value) {
            contrastState.intValue = value
            prefs.edit { putInt(CONTRAST, value) }
        }

    /** dp, or null until the user drags it. */
    var sidebarWidth by mutableStateOf(prefs.getFloat(SIDEBAR_WIDTH, -1f).takeIf { it > 0f })
        private set

    fun saveSidebarWidth(dp: Float) {
        sidebarWidth = dp
        prefs.edit { putFloat(SIDEBAR_WIDTH, dp) }
    }

    var sidebarHidden by mutableStateOf(prefs.getBoolean(SIDEBAR_HIDDEN, false))
        private set

    fun hideSidebar(hidden: Boolean) {
        sidebarHidden = hidden
        prefs.edit { putBoolean(SIDEBAR_HIDDEN, hidden) }
    }

    private companion object {
        const val THEME = "relay-theme"
        const val CONTRAST = "relay-contrast"
        const val SIDEBAR_WIDTH = "relay-sidebar-width"
        const val SIDEBAR_HIDDEN = "relay-sidebar-hidden"
    }
}
