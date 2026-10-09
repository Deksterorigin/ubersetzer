package app.deutschlern.domain.usecase

import app.deutschlern.data.remote.dto.dialog.DialogSummaryResponseDto
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.dialog.DialogSummary
import app.deutschlern.domain.model.dialog.KeyVocabularyItem
import app.deutschlern.domain.parser.StructuredJsonParser
import app.deutschlern.domain.provider.ProviderError
import app.deutschlern.domain.provider.StructuredGenerationProvider
import app.deutschlern.domain.provider.TranslationProviderRegistry
import app.deutschlern.domain.repository.DialogRepository
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.util.Prompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateDialogSummaryUseCase @Inject constructor(
    private val registry: TranslationProviderRegistry,
    private val settingsRepository: SettingsRepository,
    private val dialogRepository: DialogRepository
) {

    suspend operator fun invoke(
        dialogId: Long,
        transcript: String,
        partnerLanguage: PartnerLanguage
    ): Result<DialogSummary> = withContext(Dispatchers.Default) {
        if (transcript.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Transcript is empty"))
        }

        val settings = settingsRepository.getSettings()
        val providerId = settings.selectedCheckProviderId
        val provider = (registry.getProvider(providerId) ?: registry.geminiProvider) as? StructuredGenerationProvider
            ?: registry.geminiProvider as StructuredGenerationProvider

        val defaultModel = if (provider.id == "gemini") "gemini-2.5-flash" else "default"
        val model = settings.getSelectedModel(provider.id, isWordMode = false, defaultModel)

        val systemPrompt = Prompts.dialogSummarySystemPrompt(partnerLanguage.displayName)
        val userPrompt = Prompts.buildDialogTranscriptUserPrompt(transcript)

        val generationResult = provider.generateStructured(
            prompt = userPrompt,
            systemPrompt = systemPrompt,
            schemaJson = Prompts.DIALOG_SUMMARY_SCHEMA_SPEC,
            model = model
        )

        val rawJson = generationResult.getOrElse { error ->
            return@withContext Result.failure(error)
        }

        val parseResult = StructuredJsonParser.parse(
            rawText = rawJson,
            serializer = DialogSummaryResponseDto.serializer(),
            providerName = provider.id,
            validator = { dto ->
                dto.topic.isNotBlank() && dto.summaryDe.isNotBlank() && dto.summaryPartner.isNotBlank()
            }
        )

        val dto = parseResult.getOrElse { error ->
            return@withContext Result.failure(error)
        }

        val summary = DialogSummary(
            topic = dto.topic,
            summaryDe = dto.summaryDe,
            summaryPartner = dto.summaryPartner,
            keyPoints = dto.keyPoints,
            keyVocabulary = dto.keyVocabulary.map {
                KeyVocabularyItem(
                    german = it.german,
                    translation = it.translation,
                    contextSentence = it.contextSentence
                )
            }
        )

        // Persist summary into the dialog
        val existingDialog = dialogRepository.getDialogById(dialogId)
        if (existingDialog != null) {
            val updated = existingDialog.copy(
                summary = summary,
                summaryProviderModel = "${provider.id}:$model",
                summaryUpdatedAt = System.currentTimeMillis()
            )
            dialogRepository.updateDialog(updated)
        }

        Result.success(summary)
    }
}
