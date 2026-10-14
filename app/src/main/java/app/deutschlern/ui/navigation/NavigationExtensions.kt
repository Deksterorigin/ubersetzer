package app.deutschlern.ui.navigation

import androidx.lifecycle.SavedStateHandle

/**
 * Safely extracts a [Long] from [SavedStateHandle].
 * Handles values stored as [Long] (e.g. from Compose Navigation NavType.LongType),
 * [Number] (e.g. Int), or [String] (e.g. manual route or legacy arguments),
 * avoiding ClassCastException.
 */
fun SavedStateHandle.getNavigationLong(key: String, defaultValue: Long = 0L): Long {
    return when (val value = get<Any>(key)) {
        is Long -> value
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: defaultValue
        else -> defaultValue
    }
}
