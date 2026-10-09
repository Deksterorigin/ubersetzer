package app.deutschlern.domain.usecase

import app.deutschlern.data.remote.dto.dialog.DialogGapFillResponseDto
import app.deutschlern.data.remote.dto.dialog.DialogOpenQuestionsResponseDto
import app.deutschlern.data.remote.dto.dialog.DialogQuizResponseDto
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.dialog.DialogTask
import app.deutschlern.domain.model.dialog.DialogTaskType
import app.deutschlern.domain.model.dialog.GapFillExercise
import app.deutschlern.domain.model.dialog.GapFillTaskPayload
import app.deutschlern.domain.model.dialog.OpenQuestion
import app.deutschlern.domain.model.dialog.OpenTaskPayload
import app.deutschlern.domain.model.dialog.QuizQuestion
import app.deutschlern.domain.model.dialog.QuizTaskPayload
import app.deutschlern.domain.parser.StructuredJsonParser
import app.deutschlern.domain.provider.StructuredGenerationProvider
import app.deutschlern.domain.provider.TranslationProviderRegistry
import app.deutschlern.domain.repository.DialogRepository
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.util.Prompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateDialogTasksUseCase @Inject constructor(
    private val registry: TranslationProviderRegistry,
    private val settingsRepository: SettingsRepository,
    private val dialogRepository: DialogRepository,
    private val json: Json
) {

    suspend operator fun invoke(
        dialogId: Long,
        transcript: String,
        taskType: DialogTaskType,
        partnerLanguage: PartnerLanguage
    ): Result<DialogTask> = withContext(Dispatchers.Default) {
        if (transcript.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Transcript is empty"))
        }

        val settings = settingsRepository.getSettings()
        val providerId = settings.selectedCheckProviderId
        val provider = (registry.getProvider(providerId) ?: registry.geminiProvider) as? StructuredGenerationProvider
            ?: registry.geminiProvider as StructuredGenerationProvider

        val defaultModel = if (provider.id == "gemini") "gemini-2.5-flash" else "default"
        val model = settings.getSelectedModel(provider.id, isWordMode = false, defaultModel)
        val userPrompt = Prompts.buildDialogTranscriptUserPrompt(transcript)

        val task: DialogTask = when (taskType) {
            DialogTaskType.MCQ -> {
                val systemPrompt = Prompts.dialogQuizSystemPrompt(partnerLanguage.displayName)
                val generationResult = provider.generateStructured(
                    prompt = userPrompt,
                    systemPrompt = systemPrompt,
                    schemaJson = Prompts.DIALOG_QUIZ_SCHEMA_SPEC,
                    model = model
                )
                val rawJson = generationResult.getOrElse { return@withContext Result.failure(it) }
                val parseResult = StructuredJsonParser.parse(
                    rawText = rawJson,
                    serializer = DialogQuizResponseDto.serializer(),
                    providerName = provider.id,
                    validator = { it.questions.isNotEmpty() }
                )
                val dto = parseResult.getOrElse { return@withContext Result.failure(it) }
                val payload = QuizTaskPayload(
                    questions = dto.questions.map {
                        QuizQuestion(
                            question = it.question,
                            options = it.options,
                            correctIndex = it.correctIndex.coerceIn(0, (it.options.size - 1).coerceAtLeast(0)),
                            explanationPartner = it.explanationPartner
                        )
                    }
                )
                val payloadJson = json.encodeToString(QuizTaskPayload.serializer(), payload)
                DialogTask(
                    dialogId = dialogId,
                    type = DialogTaskType.MCQ,
                    payloadJson = payloadJson,
                    createdAt = System.currentTimeMillis()
                )
            }
            DialogTaskType.GAP_FILL -> {
                val systemPrompt = Prompts.dialogGapFillSystemPrompt(partnerLanguage.displayName)
                val generationResult = provider.generateStructured(
                    prompt = userPrompt,
                    systemPrompt = systemPrompt,
                    schemaJson = Prompts.DIALOG_GAP_FILL_SCHEMA_SPEC,
                    model = model
                )
                val rawJson = generationResult.getOrElse { return@withContext Result.failure(it) }
                val parseResult = StructuredJsonParser.parse(
                    rawText = rawJson,
                    serializer = DialogGapFillResponseDto.serializer(),
                    providerName = provider.id,
                    validator = { it.exercises.isNotEmpty() }
                )
                val dto = parseResult.getOrElse { return@withContext Result.failure(it) }
                val payload = GapFillTaskPayload(
                    exercises = dto.exercises.map {
                        GapFillExercise(
                            sentenceWithGap = it.sentenceWithGap,
                            expectedAnswer = it.expectedAnswer,
                            alternatives = it.alternatives,
                            hintPartner = it.hintPartner
                        )
                    }
                )
                val payloadJson = json.encodeToString(GapFillTaskPayload.serializer(), payload)
                DialogTask(
                    dialogId = dialogId,
                    type = DialogTaskType.GAP_FILL,
                    payloadJson = payloadJson,
                    createdAt = System.currentTimeMillis()
                )
            }
            DialogTaskType.OPEN_RESPONSE -> {
                val systemPrompt = Prompts.dialogOpenQuestionsSystemPrompt(partnerLanguage.displayName)
                val generationResult = provider.generateStructured(
                    prompt = userPrompt,
                    systemPrompt = systemPrompt,
                    schemaJson = Prompts.DIALOG_OPEN_QUESTIONS_SCHEMA_SPEC,
                    model = model
                )
                val rawJson = generationResult.getOrElse { return@withContext Result.failure(it) }
                val parseResult = StructuredJsonParser.parse(
                    rawText = rawJson,
                    serializer = DialogOpenQuestionsResponseDto.serializer(),
                    providerName = provider.id,
                    validator = { it.questions.isNotEmpty() }
                )
                val dto = parseResult.getOrElse { return@withContext Result.failure(it) }
                val payload = OpenTaskPayload(
                    questions = dto.questions.map {
                        OpenQuestion(
                            promptDe = it.promptDe,
                            promptPartner = it.promptPartner
                        )
                    }
                )
                val payloadJson = json.encodeToString(OpenTaskPayload.serializer(), payload)
                DialogTask(
                    dialogId = dialogId,
                    type = DialogTaskType.OPEN_RESPONSE,
                    payloadJson = payloadJson,
                    createdAt = System.currentTimeMillis()
                )
            }
        }

        val insertedId = dialogRepository.insertTask(task)
        val savedTask = task.copy(id = insertedId)
        Result.success(savedTask)
    }
}
