package app.deutschlern.ui.settings

import app.deutschlern.data.remote.provider.DeepLUsage

data class ProviderUiModel(
    val id: String,
    val displayName: String,
    val supportsWordMode: Boolean,
    val supportsTextMode: Boolean,
    val isOffline: Boolean,
    val apiKey: String = "",
    val isKeyVisible: Boolean = false,
    val selectedModel: String = "",
    val availableModels: List<String> = emptyList(),
    val isLoadingModels: Boolean = false,
    val testStatus: String? = null,
    val isTesting: Boolean = false,
    val keyUrl: String? = null,
    val rateLimitDocsUrl: String? = null,
    val freeTierNote: String = "",
    val deeplUsage: DeepLUsage? = null,
    val isMlKitDownloaded: Boolean = false,
    val isMlKitDownloading: Boolean = false
)

data class SettingsUiState(
    val selectedAppLanguageCode: String = "",
    val selectedWordProviderId: String = "gemini",
    val selectedTextProviderId: String = "gemini",
    val providers: List<ProviderUiModel> = emptyList(),
    val isAutoFallbackEnabled: Boolean = true,
    val fallbackOrder: List<String> = emptyList(),
    val isOfflineMlKitEnabled: Boolean = true,
    val showModelCaption: Boolean = true,
    val useCrossModelCache: Boolean = true,
    val customBaseUrl: String = "https://api.openai.com/v1",
    val customModel: String = "gpt-4o-mini",
    val openRouterShowAll: Boolean = false,
    val isKeystoreRecoveryNeeded: Boolean = false,
    val infoMessage: String? = null,
    val sttPreferOnDevice: Boolean = false,
    val sttAutoDetect: Boolean = true,
    val sttTranslateImmediately: Boolean = false,
    val selectedCheckProviderId: String = "gemini"
)
