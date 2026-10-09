package app.deutschlern.domain.provider

import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.data.remote.provider.DeepLProvider
import app.deutschlern.data.remote.provider.GeminiProvider
import app.deutschlern.data.remote.provider.MlKitOfflineProvider
import app.deutschlern.data.remote.provider.OpenAiCompatibleProvider
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationProviderRegistry @Inject constructor(
    val geminiProvider: GeminiProvider,
    val deepLProvider: DeepLProvider,
    val mlKitOfflineProvider: MlKitOfflineProvider,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val secureKeyStorage: SecureKeyStorage,
    private val rateLimiter: RateLimiter,
    private val jsonModeCapabilityStore: JsonModeCapabilityStore
) {

    private val openAiProviders = mutableMapOf<String, OpenAiCompatibleProvider>()

    init {
        // Initialize presets
        ProviderPresets.ALL_OPENAI_PRESETS.forEach { config ->
            openAiProviders[config.id] = createOpenAiProvider(config)
        }
    }

    fun updateCustomProvider(baseUrl: String, model: String) {
        val updatedConfig = ProviderPresets.CUSTOM.copy(
            baseUrl = baseUrl,
            defaultModel = model
        )
        openAiProviders[updatedConfig.id] = createOpenAiProvider(updatedConfig)
    }

    private fun createOpenAiProvider(config: ProviderConfig): OpenAiCompatibleProvider {
        return OpenAiCompatibleProvider(
            config = config,
            okHttpClient = okHttpClient,
            json = json,
            secureKeyStorage = secureKeyStorage,
            rateLimiter = rateLimiter,
            jsonModeCapabilityStore = jsonModeCapabilityStore
        )
    }

    fun getProvider(id: String): TranslationProvider? {
        return when (id) {
            geminiProvider.id -> geminiProvider
            deepLProvider.id -> deepLProvider
            mlKitOfflineProvider.id -> mlKitOfflineProvider
            else -> openAiProviders[id]
        }
    }

    fun getAllProviders(): List<TranslationProvider> {
        val list = mutableListOf<TranslationProvider>()
        list.add(geminiProvider)
        list.addAll(openAiProviders.values)
        list.add(deepLProvider)
        list.add(mlKitOfflineProvider)
        return list
    }

    fun getWordProviders(): List<TranslationProvider> {
        return getAllProviders().filter { it.capabilities.supportsWordMode }
    }

    fun getTextProviders(): List<TranslationProvider> {
        return getAllProviders().filter { it.capabilities.supportsTextMode }
    }
}
