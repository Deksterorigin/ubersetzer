package app.deutschlern.data.repository

import app.deutschlern.data.local.dao.CacheDao
import app.deutschlern.data.local.entity.CacheEntryEntity
import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.di.IoDispatcher
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.TranslationResult
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.provider.ProviderError
import app.deutschlern.domain.provider.ProviderPresets
import app.deutschlern.domain.provider.TranslationProvider
import app.deutschlern.domain.provider.TranslationProviderRegistry
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.TranslationRepository
import app.deutschlern.util.CacheKeyGenerator
import app.deutschlern.util.Prompts
import app.deutschlern.util.TextNormalizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class TranslationRepositoryImpl @Inject constructor(
    private val registry: TranslationProviderRegistry,
    private val settingsRepository: SettingsRepository,
    private val secureKeyStorage: SecureKeyStorage,
    private val cacheDao: CacheDao,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TranslationRepository {

    companion object {
        const val CACHE_TTL_MILLIS = 90L * 24 * 60 * 60 * 1000L // 90 days
        const val MODE_TEXT = "TEXT"
        const val MODE_WORD = "WORD"
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

    override suspend fun translateText(
        text: String,
        direction: TranslationDirection,
        bypassCache: Boolean
    ): Result<TranslationResult<String>> = withContext(ioDispatcher) {
        val normalizedText = TextNormalizer.normalize(text)
        if (normalizedText.isBlank()) {
            return@withContext Result.success(TranslationResult("", ""))
        }

        val settings = settingsRepository.getSettings()
        val primaryProviderId = settings.selectedTextProviderId
        val provider = registry.getProvider(primaryProviderId) ?: registry.geminiProvider
        val primaryModel = settings.getSelectedModel(provider.id, isWordMode = false, getDefaultModel(provider.id))

        val exactCacheKey = CacheKeyGenerator.generateKey(
            providerId = provider.id,
            modelName = primaryModel,
            promptVersion = Prompts.PROMPT_VERSION_TEXT,
            mode = MODE_TEXT,
            direction = direction.name,
            normalizedText = normalizedText,
            context = null
        )

        val semanticCacheKey = CacheKeyGenerator.generateSemanticKey(
            promptVersion = Prompts.PROMPT_VERSION_TEXT,
            mode = MODE_TEXT,
            direction = direction.name,
            normalizedText = normalizedText,
            context = null
        )

        val now = System.currentTimeMillis()

        // 1. Check Cache
        if (!bypassCache) {
            val exactCached = runCatching { cacheDao.get(exactCacheKey) }.getOrNull()
            if (exactCached != null && (now - exactCached.createdAt) <= CACHE_TTL_MILLIS) {
                val attribution = if (exactCached.providerId.isNotBlank()) {
                    "${exactCached.providerId} · ${exactCached.modelName}"
                } else {
                    "${provider.displayName} · $primaryModel"
                }
                return@withContext Result.success(TranslationResult(exactCached.responseJson, attribution))
            }

            if (settings.useCrossModelCache) {
                val semanticCached = runCatching { cacheDao.getBySemanticKey(semanticCacheKey) }.getOrNull()
                if (semanticCached != null && (now - semanticCached.createdAt) <= CACHE_TTL_MILLIS) {
                    val attribution = if (semanticCached.providerId.isNotBlank()) {
                        "${semanticCached.providerId} · ${semanticCached.modelName}"
                    } else {
                        "${provider.displayName} · $primaryModel"
                    }
                    return@withContext Result.success(TranslationResult(semanticCached.responseJson, attribution))
                }
            }
        }

        // 2. Perform translation with Primary Provider
        var result = provider.translateText(normalizedText, direction, primaryModel)
        var activeProvider: TranslationProvider = provider
        var activeModel: String = primaryModel
        var fallbackNotice: String? = null

        // 3. Fallback logic
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            val triggersFallback = (error is ProviderError && error.triggersFallback) || error !is ProviderError

            if (settings.isAutoFallbackEnabled && triggersFallback) {
                // If offline and ML Kit is enabled, try ML Kit first
                if (error is ProviderError.Offline && settings.isOfflineMlKitEnabled && primaryProviderId != "mlkit") {
                    val mlKitAttempt = registry.mlKitOfflineProvider.translateText(normalizedText, direction, "on-device")
                    if (mlKitAttempt.isSuccess) {
                        result = mlKitAttempt
                        activeProvider = registry.mlKitOfflineProvider
                        activeModel = "on-device"
                        fallbackNotice = "Офлайн-режим (ML Kit)"
                    }
                }

                if (result.isFailure) {
                    for (candidateId in settings.fallbackOrder) {
                        if (candidateId == primaryProviderId) continue
                        val candidate = registry.getProvider(candidateId) ?: continue
                        if (!candidate.capabilities.supportsTextMode) continue

                        val hasKey = candidate.id == "mlkit" || secureKeyStorage.getKey(candidate.id) != null
                        if (!hasKey) continue

                        val candidateModel = settings.getSelectedModel(candidate.id, isWordMode = false, getDefaultModel(candidate.id))
                        val fallbackAttempt = candidate.translateText(normalizedText, direction, candidateModel)
                        if (fallbackAttempt.isSuccess) {
                            result = fallbackAttempt
                            activeProvider = candidate
                            activeModel = candidateModel
                            fallbackNotice = "Переключено на ${candidate.displayName}"
                            break
                        }
                    }
                }
            }
        }

        // 4. Return or Cache
        if (result.isSuccess) {
            val translated = result.getOrThrow()
            runCatching {
                cacheDao.insert(
                    CacheEntryEntity(
                        cacheKey = exactCacheKey,
                        semanticKey = semanticCacheKey,
                        responseJson = translated,
                        providerId = activeProvider.displayName,
                        modelName = activeModel,
                        createdAt = now
                    )
                )
                cacheDao.deleteOlderThan(now - CACHE_TTL_MILLIS)
            }
            Result.success(
                TranslationResult(
                    data = translated,
                    modelAttribution = "${activeProvider.displayName} · $activeModel",
                    fallbackMessage = fallbackNotice
                )
            )
        } else {
            Result.failure(result.exceptionOrNull() ?: ProviderError.Generic(provider.displayName, "Ошибка перевода"))
        }
    }

    override suspend fun translateWord(
        word: String,
        direction: TranslationDirection,
        context: String?,
        bypassCache: Boolean
    ): Result<TranslationResult<WordTranslation>> = withContext(ioDispatcher) {
        val normalizedWord = TextNormalizer.normalize(word)
        if (normalizedWord.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Слово не может быть пустым"))
        }

        val normalizedContext = if (context.isNullOrBlank()) null else TextNormalizer.normalize(context)
        val settings = settingsRepository.getSettings()
        val primaryProviderId = settings.selectedWordProviderId
        val provider = registry.getProvider(primaryProviderId) ?: registry.geminiProvider
        val primaryModel = settings.getSelectedModel(provider.id, isWordMode = true, getDefaultModel(provider.id))

        val exactCacheKey = CacheKeyGenerator.generateKey(
            providerId = provider.id,
            modelName = primaryModel,
            promptVersion = Prompts.PROMPT_VERSION_WORD,
            mode = MODE_WORD,
            direction = direction.name,
            normalizedText = normalizedWord,
            context = normalizedContext
        )

        val semanticCacheKey = CacheKeyGenerator.generateSemanticKey(
            promptVersion = Prompts.PROMPT_VERSION_WORD,
            mode = MODE_WORD,
            direction = direction.name,
            normalizedText = normalizedWord,
            context = normalizedContext
        )

        val now = System.currentTimeMillis()

        // 1. Check Cache
        if (!bypassCache) {
            val exactCached = runCatching { cacheDao.get(exactCacheKey) }.getOrNull()
            if (exactCached != null && (now - exactCached.createdAt) <= CACHE_TTL_MILLIS) {
                val cachedWord = runCatching {
                    json.decodeFromString<WordTranslation>(exactCached.responseJson)
                }.getOrNull()?.let { word ->
                    val sanitized = app.deutschlern.domain.sanitizer.ArticleSanitizer.sanitize(word.lemma, word.pos, word.article)
                    word.copy(
                        lemma = sanitized.lemma,
                        article = sanitized.article,
                        pos = sanitized.pos,
                        meanings = app.deutschlern.domain.sanitizer.MeaningDeduplicator.deduplicate(word.meanings)
                    )
                }
                if (cachedWord != null) {
                    val attribution = if (exactCached.providerId.isNotBlank()) {
                        "${exactCached.providerId} · ${exactCached.modelName}"
                    } else {
                        "${provider.displayName} · $primaryModel"
                    }
                    return@withContext Result.success(TranslationResult(cachedWord, attribution))
                }
            }

            if (settings.useCrossModelCache) {
                val semanticCached = runCatching { cacheDao.getBySemanticKey(semanticCacheKey) }.getOrNull()
                if (semanticCached != null && (now - semanticCached.createdAt) <= CACHE_TTL_MILLIS) {
                    val cachedWord = runCatching {
                        json.decodeFromString<WordTranslation>(semanticCached.responseJson)
                    }.getOrNull()?.let { word ->
                        val sanitized = app.deutschlern.domain.sanitizer.ArticleSanitizer.sanitize(word.lemma, word.pos, word.article)
                        word.copy(
                            lemma = sanitized.lemma,
                            article = sanitized.article,
                            pos = sanitized.pos,
                            meanings = app.deutschlern.domain.sanitizer.MeaningDeduplicator.deduplicate(word.meanings)
                        )
                    }
                    if (cachedWord != null) {
                        val attribution = if (semanticCached.providerId.isNotBlank()) {
                            "${semanticCached.providerId} · ${semanticCached.modelName}"
                        } else {
                            "${provider.displayName} · $primaryModel"
                        }
                        return@withContext Result.success(TranslationResult(cachedWord, attribution))
                    }
                }
            }
        }

        // 2. Perform translation with Primary Provider
        var result = provider.translateWord(normalizedWord, direction, normalizedContext, primaryModel)
        var activeProvider: TranslationProvider = provider
        var activeModel: String = primaryModel
        var fallbackNotice: String? = null

        // 3. Fallback logic
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            val triggersFallback = (error is ProviderError && error.triggersFallback) || error !is ProviderError

            if (settings.isAutoFallbackEnabled && triggersFallback) {
                for (candidateId in settings.fallbackOrder) {
                    if (candidateId == primaryProviderId) continue
                    val candidate = registry.getProvider(candidateId) ?: continue
                    if (!candidate.capabilities.supportsWordMode) continue

                    val hasKey = secureKeyStorage.getKey(candidate.id) != null
                    if (!hasKey) continue

                    val candidateModel = settings.getSelectedModel(candidate.id, isWordMode = true, getDefaultModel(candidate.id))
                    val fallbackAttempt = candidate.translateWord(normalizedWord, direction, normalizedContext, candidateModel)
                    if (fallbackAttempt.isSuccess) {
                        result = fallbackAttempt
                        activeProvider = candidate
                        activeModel = candidateModel
                        fallbackNotice = "Переключено на ${candidate.displayName}"
                        break
                    }
                }
            }
        }

        // 4. Return or Cache
        if (result.isSuccess) {
            val wordResult = result.getOrThrow()
            runCatching {
                val jsonToCache = json.encodeToString(wordResult)
                cacheDao.insert(
                    CacheEntryEntity(
                        cacheKey = exactCacheKey,
                        semanticKey = semanticCacheKey,
                        responseJson = jsonToCache,
                        providerId = activeProvider.displayName,
                        modelName = activeModel,
                        createdAt = now
                    )
                )
                cacheDao.deleteOlderThan(now - CACHE_TTL_MILLIS)
            }
            Result.success(
                TranslationResult(
                    data = wordResult,
                    modelAttribution = "${activeProvider.displayName} · $activeModel",
                    fallbackMessage = fallbackNotice
                )
            )
        } else {
            Result.failure(result.exceptionOrNull() ?: ProviderError.Generic(provider.displayName, "Ошибка перевода"))
        }
    }
}
