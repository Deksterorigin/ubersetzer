package app.deutschlern.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Localized text for ViewModels and errors, which live outside Compose. MainActivity keeps
 * Locale.getDefault() in sync with the language chosen in the app, so that locale is used here.
 */
object AppStrings {

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cached: Pair<Locale, Resources>? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any): String {
        // Unit tests run without Android resources and compare against this same call
        val context = appContext ?: return listOf(id, *args).joinToString("|")
        return resourcesFor(context, Locale.getDefault()).getString(id, *args)
    }

    private fun resourcesFor(context: Context, locale: Locale): Resources {
        cached?.takeIf { it.first == locale }?.let { return it.second }
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        val resources = context.createConfigurationContext(config).resources
        cached = locale to resources
        return resources
    }
}
