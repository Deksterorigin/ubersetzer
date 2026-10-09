package app.deutschlern.data.remote.provider

import android.content.Context
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.provider.ProviderCapabilities
import app.deutschlern.domain.provider.ProviderError
import app.deutschlern.domain.provider.TranslationProvider
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

@Singleton
class MlKitOfflineProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : TranslationProvider {

    override val id: String = "mlkit"
    override val displayName: String = "ML Kit (Офлайн)"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsWordMode = false,
        supportsTextMode = true,
        isOffline = true
    )

    private val translators = ConcurrentHashMap<TranslationDirection, Translator>()
    private val modelManager = RemoteModelManager.getInstance()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private fun getGermanModel(): TranslateRemoteModel =
        TranslateRemoteModel.Builder(TranslateLanguage.GERMAN).build()

    private fun getRussianModel(): TranslateRemoteModel =
        TranslateRemoteModel.Builder(TranslateLanguage.RUSSIAN).build()

    suspend fun areModelsDownloaded(): Boolean = withContext(Dispatchers.IO) {
        try {
            val deDownloaded = modelManager.isModelDownloaded(getGermanModel()).await()
            val ruDownloaded = modelManager.isModelDownloaded(getRussianModel()).await()
            deDownloaded && ruDownloaded
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadModels(requireWifi: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        _isDownloading.value = true
        try {
            val conditionsBuilder = DownloadConditions.Builder()
            if (requireWifi) {
                conditionsBuilder.requireWifi()
            }
            val conditions = conditionsBuilder.build()

            modelManager.download(getGermanModel(), conditions).await()
            modelManager.download(getRussianModel(), conditions).await()
            _isDownloading.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            _isDownloading.value = false
            Result.failure(ProviderError.Generic(displayName, "Ошибка скачивания моделей: ${e.localizedMessage}"))
        }
    }

    private fun mapLanguage(code: String): String {
        return when (code.lowercase()) {
            "de" -> TranslateLanguage.GERMAN
            "ru" -> TranslateLanguage.RUSSIAN
            "en" -> TranslateLanguage.ENGLISH
            "uk" -> TranslateLanguage.UKRAINIAN
            "ar" -> TranslateLanguage.ARABIC
            "ro" -> TranslateLanguage.ROMANIAN
            "fa" -> TranslateLanguage.PERSIAN
            else -> TranslateLanguage.fromLanguageTag(code) ?: TranslateLanguage.GERMAN
        }
    }

    private fun getOrCreateTranslator(direction: TranslationDirection): Translator {
        return translators.computeIfAbsent(direction) {
            val source = mapLanguage(direction.sourceCode)
            val target = mapLanguage(direction.targetCode)
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
            Translation.getClient(options)
        }
    }

    override suspend fun translateWord(
        word: String,
        direction: TranslationDirection,
        context: String?,
        model: String
    ): Result<WordTranslation> {
        return Result.failure(
            ProviderError.Generic(displayName, "ML Kit не поддерживает словарный режим (WORD). Доступен только для текста.")
        )
    }

    override suspend fun translateText(
        text: String,
        direction: TranslationDirection,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val translator = getOrCreateTranslator(direction)
            // Ensure model is downloaded
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()

            val translated = translator.translate(text).await()
            Result.success(translated)
        } catch (e: Exception) {
            Result.failure(
                ProviderError.Generic(displayName, "Ошибка офлайн-перевода ML Kit: ${e.localizedMessage}")
            )
        }
    }

    override suspend fun testConnection(model: String): Result<String> = withContext(Dispatchers.IO) {
        val downloaded = areModelsDownloaded()
        if (downloaded) {
            Result.success("Модели загружены и готовы к работе")
        } else {
            Result.success("Требуется загрузка языковых моделей (DE/RU)")
        }
    }

    override suspend fun listModels(): Result<List<String>> {
        return Result.success(listOf("on-device"))
    }

    private suspend fun <T> Task<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result ->
                cont.resume(result, null)
            }
            addOnFailureListener { exception ->
                cont.resumeWithException(exception)
            }
            addOnCanceledListener {
                cont.cancel()
            }
        }
}
