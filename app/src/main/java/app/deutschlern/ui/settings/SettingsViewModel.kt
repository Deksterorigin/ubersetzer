package app.deutschlern.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.data.local.dao.CacheDao
import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.data.remote.provider.DeepLProvider
import app.deutschlern.data.remote.provider.MlKitOfflineProvider
import app.deutschlern.domain.provider.ProviderPresets
import app.deutschlern.domain.provider.TranslationProviderRegistry
import app.deutschlern.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStorage: SecureKeyStorage,
    private val registry: TranslationProviderRegistry,
    private val cacheDao: CacheDao,
    private val mlKitOfflineProvider: MlKitOfflineProvider,
    private val deepLProvider: DeepLProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
        observeKeystoreRecovery()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                val allProviders = registry.getAllProviders()
                val providerModels = allProviders.map { provider ->
                    val existingModel = _uiState.value.providers.find { it.id == provider.id }
                    val key = secureKeyStorage.getKey(provider.id).orEmpty()
                    val defaultModel = getDefaultModel(provider.id)
                    val selectedModel = settings.getSelectedModel(provider.id, isWordMode = provider.capabilities.supportsWordMode, defaultModel)

                    val presetConfig = ProviderPresets.ALL_OPENAI_PRESETS.find { it.id == provider.id }

                    val isMlKitDownloaded = if (provider.id == "mlkit") mlKitOfflineProvider.areModelsDownloaded() else false

                    ProviderUiModel(
                        id = provider.id,
                        displayName = provider.displayName,
                        supportsWordMode = provider.capabilities.supportsWordMode,
                        supportsTextMode = provider.capabilities.supportsTextMode,
                        isOffline = provider.capabilities.isOffline,
                        apiKey = existingModel?.apiKey ?: key,
                        isKeyVisible = existingModel?.isKeyVisible ?: false,
                        selectedModel = selectedModel,
                        availableModels = existingModel?.availableModels ?: emptyList(),
                        isLoadingModels = existingModel?.isLoadingModels ?: false,
                        testStatus = existingModel?.testStatus,
                        isTesting = existingModel?.isTesting ?: false,
                        keyUrl = when (provider.id) {
                            "gemini" -> "https://aistudio.google.com/app/apikey"
                            "deepl" -> "https://www.deepl.com/pro-api"
                            else -> presetConfig?.keyUrl
                        },
                        rateLimitDocsUrl = when (provider.id) {
                            "gemini" -> "https://ai.google.dev/pricing"
                            "deepl" -> "https://www.deepl.com/pro-api"
                            else -> presetConfig?.rateLimitDocsUrl
                        },
                        freeTierNote = when (provider.id) {
                            "gemini" -> "Google Gemini Free Tier (до 15 запросов в минуту):"
                            "deepl" -> "DeepL API Free: 500 000 знаков в месяц бесплатно."
                            "mlkit" -> "Полностью бесплатно и работает без интернета."
                            else -> presetConfig?.freeTierNote.orEmpty()
                        },
                        deeplUsage = existingModel?.deeplUsage,
                        isMlKitDownloaded = isMlKitDownloaded
                    )
                }

                _uiState.update { current ->
                    current.copy(
                        selectedAppLanguageCode = settings.appLanguageCode,
                        selectedWordProviderId = settings.selectedWordProviderId,
                        selectedTextProviderId = settings.selectedTextProviderId,
                        providers = providerModels,
                        isAutoFallbackEnabled = settings.isAutoFallbackEnabled,
                        fallbackOrder = settings.fallbackOrder,
                        isOfflineMlKitEnabled = settings.isOfflineMlKitEnabled,
                        showModelCaption = settings.showModelCaption,
                        useCrossModelCache = settings.useCrossModelCache,
                        customBaseUrl = settings.customProviderBaseUrl,
                        customModel = settings.customProviderModel,
                        openRouterShowAll = settings.openRouterShowAllModels,
                        sttPreferOnDevice = settings.sttPreferOnDevice,
                        sttAutoDetect = settings.sttAutoDetect,
                        sttTranslateImmediately = settings.sttTranslateImmediately,
                        selectedCheckProviderId = settings.selectedCheckProviderId
                    )
                }
            }
        }
    }

    private fun observeKeystoreRecovery() {
        viewModelScope.launch {
            secureKeyStorage.isKeyRecoveryNeeded.collect { needed ->
                _uiState.update { it.copy(isKeystoreRecoveryNeeded = needed) }
            }
        }
    }

    fun onSelectAppLanguage(languageCode: String) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(languageCode)
        }
    }

    fun onSelectWordProvider(providerId: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedWordProvider(providerId)
        }
    }

    fun onSelectTextProvider(providerId: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedTextProvider(providerId)
        }
    }

    fun onSelectCheckProvider(providerId: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedCheckProvider(providerId)
        }
    }

    fun onApiKeyChanged(providerId: String, key: String) {
        _uiState.update { state ->
            val updated = state.providers.map { p ->
                if (p.id == providerId) p.copy(apiKey = key) else p
            }
            state.copy(providers = updated)
        }
        viewModelScope.launch {
            secureKeyStorage.saveKey(providerId, key)
            if (providerId == "deepl" && key.isNotBlank()) {
                fetchDeepLUsage()
            }
        }
    }

    fun onToggleKeyVisibility(providerId: String) {
        _uiState.update { state ->
            val updated = state.providers.map { p ->
                if (p.id == providerId) p.copy(isKeyVisible = !p.isKeyVisible) else p
            }
            state.copy(providers = updated)
        }
    }

    fun onModelChanged(providerId: String, model: String) {
        _uiState.update { state ->
            val updated = state.providers.map { p ->
                if (p.id == providerId) p.copy(selectedModel = model) else p
            }
            state.copy(providers = updated)
        }
        viewModelScope.launch {
            settingsRepository.setSelectedModel(providerId, isWordMode = true, model)
            settingsRepository.setSelectedModel(providerId, isWordMode = false, model)
        }
    }

    fun onLoadModelsClicked(providerId: String) {
        val provider = registry.getProvider(providerId) ?: return
        _uiState.update { state ->
            val updated = state.providers.map { p ->
                if (p.id == providerId) p.copy(isLoadingModels = true, testStatus = null) else p
            }
            state.copy(providers = updated)
        }

        viewModelScope.launch {
            val result = provider.listModels()
            _uiState.update { state ->
                val updated = state.providers.map { p ->
                    if (p.id == providerId) {
                        if (result.isSuccess) {
                            var models = result.getOrThrow()
                            if (providerId == "openrouter" && !state.openRouterShowAll) {
                                models = models.filter { it.endsWith(":free", ignoreCase = true) }
                            }
                            p.copy(
                                isLoadingModels = false,
                                availableModels = models,
                                testStatus = "Загружено ${models.size} моделей"
                            )
                        } else {
                            p.copy(
                                isLoadingModels = false,
                                testStatus = result.exceptionOrNull()?.localizedMessage ?: "Ошибка загрузки моделей"
                            )
                        }
                    } else p
                }
                state.copy(providers = updated)
            }
        }
    }

    fun onTestConnectionClicked(providerId: String) {
        val provider = registry.getProvider(providerId) ?: return
        val currentModel = _uiState.value.providers.find { it.id == providerId }?.selectedModel
            ?: getDefaultModel(providerId)

        _uiState.update { state ->
            val updated = state.providers.map { p ->
                if (p.id == providerId) p.copy(isTesting = true, testStatus = "Проверка…") else p
            }
            state.copy(providers = updated)
        }

        viewModelScope.launch {
            val result = provider.testConnection(currentModel)
            _uiState.update { state ->
                val updated = state.providers.map { p ->
                    if (p.id == providerId) {
                        p.copy(
                            isTesting = false,
                            testStatus = if (result.isSuccess) result.getOrThrow() else (result.exceptionOrNull()?.localizedMessage ?: "Ошибка соединения")
                        )
                    } else p
                }
                state.copy(providers = updated)
            }
            if (providerId == "deepl") {
                fetchDeepLUsage()
            }
        }
    }

    private suspend fun fetchDeepLUsage() {
        val usageResult = deepLProvider.getUsage()
        if (usageResult.isSuccess) {
            val usage = usageResult.getOrThrow()
            _uiState.update { state ->
                val updated = state.providers.map { p ->
                    if (p.id == "deepl") p.copy(deeplUsage = usage) else p
                }
                state.copy(providers = updated)
            }
        }
    }

    fun onToggleAutoFallback(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoFallbackEnabled(enabled)
        }
    }

    fun onMoveFallbackProvider(fromIndex: Int, toIndex: Int) {
        val currentOrder = _uiState.value.fallbackOrder.toMutableList()
        if (fromIndex in currentOrder.indices && toIndex in currentOrder.indices) {
            val item = currentOrder.removeAt(fromIndex)
            currentOrder.add(toIndex, item)
            viewModelScope.launch {
                settingsRepository.setFallbackOrder(currentOrder)
            }
        }
    }

    fun onToggleOfflineMlKit(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOfflineMlKitEnabled(enabled)
        }
    }

    fun onDownloadMlKitClicked() {
        _uiState.update { state ->
            val updated = state.providers.map { p ->
                if (p.id == "mlkit") p.copy(isMlKitDownloading = true) else p
            }
            state.copy(providers = updated)
        }
        viewModelScope.launch {
            val result = mlKitOfflineProvider.downloadModels(requireWifi = false)
            val downloaded = mlKitOfflineProvider.areModelsDownloaded()
            _uiState.update { state ->
                val updated = state.providers.map { p ->
                    if (p.id == "mlkit") {
                        p.copy(
                            isMlKitDownloading = false,
                            isMlKitDownloaded = downloaded,
                            testStatus = if (result.isSuccess) "Модели загружены" else result.exceptionOrNull()?.localizedMessage
                        )
                    } else p
                }
                state.copy(providers = updated)
            }
        }
    }

    fun onToggleShowModelCaption(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowModelCaption(show)
        }
    }

    fun onToggleUseCrossModelCache(use: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseCrossModelCache(use)
        }
    }

    fun onClearCacheClicked() {
        viewModelScope.launch {
            cacheDao.clearAll()
            _uiState.update { it.copy(infoMessage = "Кэш переводов очищен") }
        }
    }

    fun onCustomConfigChanged(baseUrl: String, model: String) {
        viewModelScope.launch {
            settingsRepository.setCustomProviderConfig(baseUrl, model)
            registry.updateCustomProvider(baseUrl, model)
        }
    }

    fun onToggleOpenRouterShowAll(showAll: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOpenRouterShowAllModels(showAll)
            onLoadModelsClicked("openrouter")
        }
    }

    fun onToggleSttPreferOnDevice(prefer: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSttPreferOnDevice(prefer)
        }
    }

    fun onToggleSttAutoDetect(auto: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSttAutoDetect(auto)
        }
    }

    fun onToggleSttTranslateImmediately(immediate: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSttTranslateImmediately(immediate)
        }
    }

    fun onDismissRecoveryNotice() {
        secureKeyStorage.dismissRecoveryNotice()
    }

    fun onDismissInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    private fun getDefaultModel(providerId: String): String = when (providerId) {
        "gemini" -> "gemini-2.5-flash"
        "groq" -> ProviderPresets.GROQ.defaultModel
        "mistral" -> ProviderPresets.MISTRAL.defaultModel
        "cerebras" -> ProviderPresets.CEREBRAS.defaultModel
        "openrouter" -> ProviderPresets.OPENROUTER.defaultModel
        "custom" -> ProviderPresets.CUSTOM.defaultModel
        "deepl" -> "default"
        "mlkit" -> "on-device"
        else -> "default"
    }
}
