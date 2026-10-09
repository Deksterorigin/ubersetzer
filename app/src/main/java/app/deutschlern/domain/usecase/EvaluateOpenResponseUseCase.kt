package app.deutschlern.domain.usecase

import app.deutschlern.data.remote.dto.dialog.OpenAnswerEvaluationResponseDto
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.dialog.OpenEvaluation
import app.deutschlern.domain.parser.StructuredJsonParser
import app.deutschlern.domain.provider.StructuredGenerationProvider
import app.deutschlern.domain.provider.TranslationProviderRegistry
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.util.Prompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvaluateOpenResponseUseCase @Inject constructor(
    private val registry: TranslationProviderRegistry,
    private val settingsRepository: SettingsRepository
) {

    suspend operator fun invoke(
        questionDe: String,
        studentAnswer: String,
        partnerLanguage: PartnerLanguage
    ): Result<OpenEvaluation> = withContext(Dispatchers.Default) {
        if (studentAnswer.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Student answer is empty"))
        }

        val settings = settingsRepository.getSettings()
        val providerId = settings.selectedCheckProviderId
        val provider = (registry.getProvider(providerId) ?: registry.geminiProvider) as? StructuredGenerationProvider
            ?: registry.geminiProvider as StructuredGenerationProvider

        val defaultModel = if (provider.id == "gemini") "gemini-2.5-flash" else "default"
        val model = settings.getSelectedModel(provider.id, isWordMode = false, defaultModel)

        val systemPrompt = Prompts.dialogOpenEvaluationSystemPrompt(partnerLanguage.displayName)
        val userPrompt = Prompts.buildOpenAnswerUserPrompt(questionDe, studentAnswer)

        val generationResult = provider.generateStructured(
            prompt = userPrompt,
            systemPrompt = systemPrompt,
            schemaJson = Prompts.DIALOG_OPEN_EVALUATION_SCHEMA_SPEC,
            model = model
        )

        val rawJson = generationResult.getOrElse { return@withContext Result.failure(it) }

        val parseResult = StructuredJsonParser.parse(
            rawText = rawJson,
            serializer = OpenAnswerEvaluationResponseDto.serializer(),
            providerName = provider.id
        )

        val dto = parseResult.getOrElse { return@withContext Result.failure(it) }

        val evaluation = OpenEvaluation(
            scorePercent = dto.scorePercent.coerceIn(0, 100),
            corrections = dto.corrections,
            improvedVersion = dto.improvedVersion,
            feedbackPartner = dto.feedbackPartner
        )

        Result.success(evaluation)
    }
}
