package app.deutschlern

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.text.TextUtils
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import app.deutschlern.data.local.AppDatabase
import app.deutschlern.domain.model.AppLanguage
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.ui.navigation.AppNavigation
import app.deutschlern.ui.theme.DeutschLernTheme
import app.deutschlern.util.PendingTranslationHolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var pendingTranslationHolder: PendingTranslationHolder

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var legacyArticleRepairManager: app.deutschlern.data.local.LegacyArticleRepairManager

    private val pendingNavRoute = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                appDatabase.openHelper.writableDatabase
                android.util.Log.i("MainActivity", "AppDatabase initialized successfully")
                legacyArticleRepairManager.repairIfNeeded()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "AppDatabase initialization or repair error", e)
            }
        }

        // Sync Android 13+ system-level per-app language into DataStore if app settings are unset
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = getSystemService(LocaleManager::class.java)
                if (localeManager != null && !localeManager.applicationLocales.isEmpty) {
                    val systemAppTag = localeManager.applicationLocales[0]?.toLanguageTag().orEmpty()
                    lifecycleScope.launch {
                        val currentSettings = settingsRepository.getSettings()
                        if (currentSettings.appLanguageCode.isBlank() && systemAppTag.isNotBlank()) {
                            val matched = AppLanguage.fromCode(systemAppTag)
                            if (matched != AppLanguage.SYSTEM) {
                                settingsRepository.setAppLanguage(matched.code)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        setContent {
            val settings by settingsRepository.settingsFlow.collectAsState(initial = null)
            val currentSettings = settings

            // Do not run effects or resolve incomplete locale state while settings are loading
            if (currentSettings == null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {}
                return@setContent
            }

            val appLanguage = currentSettings.selectedAppLanguage

            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current

            val (localizedContext, localizedConfig, layoutDirection) = remember(appLanguage, baseConfig) {
                if (appLanguage == AppLanguage.SYSTEM) {
                    val sysLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
                    } else {
                        @Suppress("DEPRECATION")
                        Resources.getSystem().configuration.locale ?: Locale.getDefault()
                    }
                    Locale.setDefault(sysLocale)
                    val config = Configuration(baseConfig).apply {
                        setLocale(sysLocale)
                        setLayoutDirection(sysLocale)
                    }
                    val ctx = LocalizedActivityContext(baseContext, config)
                    val isRtl = TextUtils.getLayoutDirectionFromLocale(sysLocale) == View.LAYOUT_DIRECTION_RTL
                    Triple(ctx, config, if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)
                } else {
                    val targetLocale = Locale.forLanguageTag(appLanguage.bcp47Tag)
                    Locale.setDefault(targetLocale)
                    val config = Configuration(baseConfig).apply {
                        setLocale(targetLocale)
                        setLayoutDirection(targetLocale)
                    }
                    val ctx = LocalizedActivityContext(baseContext, config)
                    val dir = if (appLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    Triple(ctx, config, dir)
                }
            }

            LaunchedEffect(appLanguage) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        val localeManager = getSystemService(LocaleManager::class.java)
                        if (localeManager != null) {
                            val targetLocales = if (appLanguage.code.isEmpty()) {
                                LocaleList.getEmptyLocaleList()
                            } else {
                                LocaleList.forLanguageTags(appLanguage.bcp47Tag)
                            }
                            if (localeManager.applicationLocales != targetLocales) {
                                localeManager.applicationLocales = targetLocales
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext,
                LocalLayoutDirection provides layoutDirection,
                app.deutschlern.ui.theme.LocalColoredArticlesEnabled provides currentSettings.coloredArticlesEnabled
            ) {
                DeutschLernTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = androidx.navigation.compose.rememberNavController()
                        val targetRoute = pendingNavRoute.value
                        LaunchedEffect(targetRoute) {
                            if (!targetRoute.isNullOrBlank()) {
                                navController.navigate(targetRoute)
                                pendingNavRoute.value = null
                            }
                        }
                        AppNavigation(navController = navController)
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        intent.getStringExtra("navigate_route")?.let { route ->
            pendingNavRoute.value = route
        }
        val text = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            }
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                } else null
            }
            else -> null
        }

        if (!text.isNullOrBlank()) {
            pendingTranslationHolder.setPendingText(text)
        }
    }
}

private class LocalizedActivityContext(
    base: Context,
    private val config: Configuration
) : ContextWrapper(base) {
    private val localizedRes: Resources by lazy {
        base.createConfigurationContext(config).resources
    }
    override fun getResources(): Resources = localizedRes
}
