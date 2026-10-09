package app.deutschlern.domain.usecase

import app.deutschlern.data.local.dao.CacheDao
import app.deutschlern.data.local.entity.CacheEntryEntity
import app.deutschlern.data.remote.dto.check.CorrectionResponseDto
import app.deutschlern.domain.check.OverEditGuard
import app.deutschlern.domain.check.TokenDiffEngine
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.check.CheckCategory
import app.deutschlern.domain.model.check.CheckComment
import app.deutschlern.domain.model.check.CommentIssue
import app.deutschlern.domain.model.check.TextCheckResult
import app.deutschlern.domain.parser.StructuredJsonParser
import app.deutschlern.domain.provider.ProviderError
import app.deutschlern.domain.provider.StructuredGenerationProvider
import app.deutschlern.domain.provider.TranslationProviderRegistry
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.util.CacheKeyGenerator
import app.deutschlern.util.Prompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckTextUseCase @Inject constructor(
    private val registry: TranslationProviderRegistry,
    private val settingsRepository: SettingsRepository,
    private val cacheDao: CacheDao,
    private val json: Json
) {

    suspend operator fun invoke(
        text: String,
        partnerLanguage: PartnerLanguage,
        bypassCache: Boolean = false
    ): Result<TextCheckResult> = withContext(Dispatchers.Default) {
        val normalizedOriginal = OverEditGuard.normalizeText(text)
        if (normalizedOriginal.isBlank()) {
            return@withContext Result.success(
                TextCheckResult(
                    originalText = text,
                    correctedText = text,
                    hunks = emptyList(),
                    comments = emptyList(),
                    partnerLanguage = partnerLanguage.code
                )
            )
        }

        val settings = settingsRepository.getSettings()
        val providerId = settings.selectedCheckProviderId
        val provider = (registry.getProvider(providerId) ?: registry.geminiProvider) as? StructuredGenerationProvider
            ?: registry.geminiProvider as StructuredGenerationProvider

        val defaultModel = if (provider.id == "gemini") "gemini-2.5-flash" else "default"
        val model = settings.getSelectedModel(provider.id, isWordMode = false, defaultModel)

        // Split text into paragraphs preserving structure
        val paragraphDelimiter = "\n\n"
        val rawParagraphs = normalizedOriginal.split(paragraphDelimiter)

        val correctedParagraphs = mutableListOf<String>()
        val aggregatedEdits = mutableListOf<app.deutschlern.data.remote.dto.check.EditItemDto>()
        val aggregatedComments = mutableListOf<CheckComment>()
        var hasOverEditWarning = false

        for (index in rawParagraphs.indices) {
            val paragraph = rawParagraphs[index]
            if (paragraph.isBlank()) {
                correctedParagraphs.add(paragraph)
                continue
            }

            val previousContext = if (index > 0) rawParagraphs[index - 1].takeIf { it.isNotBlank() } else null

            val cacheKey = CacheKeyGenerator.generateCheckKey(
                providerId = provider.id,
                modelName = model,
                promptVersion = Prompts.PROMPT_VERSION_CHECK,
                partnerLanguage = partnerLanguage.code,
                normalizedText = paragraph.trim()
            )

            var cachedDto: CorrectionResponseDto? = null
            if (!bypassCache) {
                val cached = runCatching { cacheDao.get(cacheKey) }.getOrNull()
                if (cached != null) {
                    cachedDto = runCatching {
                        json.decodeFromString<CorrectionResponseDto>(cached.responseJson)
                    }.getOrNull()
                }
            }

            val paragraphResultDto: CorrectionResponseDto
            if (cachedDto != null) {
                paragraphResultDto = cachedDto
            } else {
                // Call provider
                val userPrompt = Prompts.buildCheckTextUserPrompt(paragraph, previousContext)
                val systemPrompt = Prompts.checkTextSystemPrompt(partnerLanguage.displayName, isStrict = false)

                val responseRaw = provider.generateStructured(
                    prompt = userPrompt,
                    systemPrompt = systemPrompt,
                    schemaJson = Prompts.CHECK_TEXT_SCHEMA_SPEC,
                    model = model
                ).getOrElse { error ->
                    return@withContext Result.failure(error)
                }

                val parsedFirst = StructuredJsonParser.parse(
                    rawText = responseRaw,
                    serializer = CorrectionResponseDto.serializer(),
                    providerName = provider.displayName
                ).getOrElse { error ->
                    return@withContext Result.failure(error)
                }

                // Evaluate over-edit
                val eval = OverEditGuard.evaluate(paragraph, parsedFirst.correctedText)
                if (eval.isOverEdit) {
                    // Single stricter retry
                    val strictSystemPrompt = Prompts.checkTextSystemPrompt(partnerLanguage.displayName, isStrict = true)
                    val retryRaw = provider.generateStructured(
                        prompt = userPrompt,
                        systemPrompt = strictSystemPrompt,
                        schemaJson = Prompts.CHECK_TEXT_SCHEMA_SPEC,
                        model = model
                    ).getOrNull()

                    val parsedRetry = retryRaw?.let {
                        StructuredJsonParser.parse(
                            rawText = it,
                            serializer = CorrectionResponseDto.serializer(),
                            providerName = provider.displayName
                        ).getOrNull()
                    }

                    if (parsedRetry != null) {
                        val retryEval = OverEditGuard.evaluate(paragraph, parsedRetry.correctedText)
                        if (retryEval.isOverEdit) {
                            hasOverEditWarning = true
                        }
                        paragraphResultDto = parsedRetry
                    } else {
                        hasOverEditWarning = true
                        paragraphResultDto = parsedFirst
                    }
                } else {
                    paragraphResultDto = parsedFirst
                }

                // Cache successful result
                runCatching {
                    cacheDao.insert(
                        CacheEntryEntity(
                            cacheKey = cacheKey,
                            semanticKey = "",
                            responseJson = json.encodeToString(paragraphResultDto),
                            providerId = provider.id,
                            modelName = model,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            correctedParagraphs.add(paragraphResultDto.correctedText)
            aggregatedEdits.addAll(paragraphResultDto.edits)
            paragraphResultDto.comments.forEach { cDto ->
                aggregatedComments.add(
                    CheckComment(
                        fragment = cDto.fragment,
                        issue = CommentIssue.fromString(cDto.issue),
                        explanation = cDto.explanation
                    )
                )
            }
        }

        val mergedCorrectedText = correctedParagraphs.joinToString(paragraphDelimiter)

        // Compute client-side diff
        val rawHunks = TokenDiffEngine.computeDiff(normalizedOriginal, mergedCorrectedText)
        val matchedHunks = TokenDiffEngine.matchHunksToModelEdits(
            rawHunks = rawHunks,
            modelEdits = aggregatedEdits
        )

        val result = TextCheckResult(
            originalText = normalizedOriginal,
            correctedText = mergedCorrectedText,
            hunks = matchedHunks,
            comments = aggregatedComments,
            isOverEditWarning = hasOverEditWarning,
            providerModel = "${provider.displayName} · $model",
            partnerLanguage = partnerLanguage.code
        )

        Result.success(result)
    }
}
