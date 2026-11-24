package app.deutschlern.domain.usecase

import app.deutschlern.R
import app.deutschlern.data.remote.dto.exam.ExamEvaluationResponseDto
import app.deutschlern.domain.exam.ExamCorrection
import app.deutschlern.domain.exam.ExamCriterion
import app.deutschlern.domain.exam.ExamEvaluation
import app.deutschlern.domain.exam.ExamResponseKind
import app.deutschlern.domain.exam.WritingKind
import app.deutschlern.domain.parser.StructuredJsonParser
import app.deutschlern.domain.provider.StructuredGenerationProvider
import app.deutschlern.domain.provider.TranslationProviderRegistry
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.util.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The exam task a text answers, as shown to the candidate. */
data class ExamTaskDescription(
    val title: String? = null,
    val situation: String,
    val points: List<String> = emptyList(),
    val writingKind: WritingKind? = null,
    val minWords: Int? = null
)

/**
 * Grades a German exam text (letter, forum post) or a spoken-answer transcript against its task
 * by the telc Deutsch-Test für den Beruf B2 criteria, with explanations in Russian.
 */
@Singleton
class EvaluateExamWritingUseCase @Inject constructor(
    private val registry: TranslationProviderRegistry,
    private val settingsRepository: SettingsRepository
) {

    suspend operator fun invoke(
        task: ExamTaskDescription,
        text: String,
        kind: ExamResponseKind = ExamResponseKind.WRITING
    ): Result<ExamEvaluation> = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException(AppStrings.get(R.string.exam_error_text_empty)))
        }

        val settings = settingsRepository.getSettings()
        val provider = (registry.getProvider(settings.selectedCheckProviderId) ?: registry.geminiProvider) as? StructuredGenerationProvider
            ?: registry.geminiProvider as StructuredGenerationProvider
        val defaultModel = if (provider.id == "gemini") "gemini-2.5-flash" else "default"
        val model = settings.getSelectedModel(provider.id, isWordMode = false, defaultModel)

        val raw = provider.generateStructured(
            prompt = buildUserPrompt(task, text),
            systemPrompt = systemPrompt(kind, task.writingKind),
            schemaJson = SCHEMA,
            model = model
        ).getOrElse { return@withContext Result.failure(it) }

        val dto = StructuredJsonParser.parse(
            rawText = raw,
            serializer = ExamEvaluationResponseDto.serializer(),
            providerName = provider.displayName
        ).getOrElse { return@withContext Result.failure(it) }

        Result.success(dto.toDomain())
    }

    companion object {

        internal fun ExamEvaluationResponseDto.toDomain() = ExamEvaluation(
            score = score.coerceIn(0, 100),
            summary = summary.trim(),
            criteria = criteria
                .filter { it.name.isNotBlank() || it.comment.isNotBlank() }
                .map { ExamCriterion(it.name.trim(), it.score?.coerceIn(0, 100), it.comment.trim()) },
            corrections = corrections
                .filter { it.original.isNotBlank() && it.original.trim() != it.corrected.trim() }
                .map { ExamCorrection(it.original.trim(), it.corrected.trim(), it.reason.trim()) },
            correctedText = correctedText.trim()
        )

        internal fun buildUserPrompt(task: ExamTaskDescription, text: String): String = buildString {
            append("<task>\n")
            task.title?.takeIf { it.isNotBlank() }?.let { append("Titel: ").append(it.trim()).append('\n') }
            append(task.situation.trim()).append('\n')
            if (task.points.isNotEmpty()) {
                append("Inhaltspunkte:\n")
                task.points.forEach { append("- ").append(it.trim()).append('\n') }
            }
            task.minWords?.let { append("Mindestens $it Wörter.\n") }
            append("</task>\n\n<candidate_text>\n")
            append(text.trim())
            append("\n</candidate_text>")
        }

        internal fun systemPrompt(kind: ExamResponseKind, writingKind: WritingKind?): String {
            val form = when {
                kind == ExamResponseKind.SPEAKING ->
                    "The text is a speech-to-text transcript of a spoken answer in the oral exam (Sprechen). " +
                        "Ignore punctuation, capitalisation and obvious recognition errors; judge fluency of argumentation, " +
                        "structure (introduction, points, conclusion), range of Redemittel and grammar as spoken language. " +
                        "Criterion \"Form/Register\" means appropriate polite register and discourse markers."
                writingKind == WritingKind.BESCHWERDE ->
                    "The text is a formal business letter / e-mail (Beschwerde or reply to a complaint). " +
                        "Criterion \"Form/Register\" checks Betreff, formal Anrede (Sehr geehrte ...), Sie-form, " +
                        "a closing formula (Mit freundlichen Grüßen) and the sender's name."
                writingKind == WritingKind.FORUM ->
                    "The text is a forum post (Forumsbeitrag) giving an opinion on a workplace topic. " +
                        "Criterion \"Form/Register\" checks a suitable greeting, neutral-polite register, clear opinion and a closing."
                else -> "The text is a written exam answer."
            }
            return """
You are a strict but supportive certified telc examiner for the "Deutsch-Test für den Beruf B2".
Grade the candidate's German text against the task by the telc DTB B2 criteria.
$form
Criteria (use exactly these names, in this order):
1. "Aufgabenerfüllung" — are all Inhaltspunkte of the task covered and developed, is the text relevant.
2. "Kohärenz" — logical structure, connectors, paragraphs.
3. "Wortschatz" — range and precision of vocabulary, professional register.
4. "Grammatik" — morphology and syntax correctness.
5. "Form/Register" — as described above.
Return:
- "score": integer 0–100 for the whole text (60 = just passing B2).
- "summary": 1–2 sentences in Russian with the overall verdict.
- "criteria": one object per criterion with "name", "score" (0–100) and "comment" (short, in Russian, name missing points explicitly).
- "corrections": the real mistakes, each with "original" (exact fragment from the text), "corrected" (fixed German fragment) and "reason" (very short, in Russian). Do not list stylistic preferences as mistakes. Empty list if none.
- "corrected_text": the candidate's text with all mistakes corrected, keeping their ideas and structure (do not rewrite into a model answer).
All explanations must be in Russian; German fragments stay German.
Output strictly one valid JSON object matching the schema, no markdown.
""".trimIndent()
        }

        const val SCHEMA = """{
  "type": "object",
  "properties": {
    "score": { "type": "integer" },
    "summary": { "type": "string" },
    "criteria": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": { "type": "string" },
          "score": { "type": "integer" },
          "comment": { "type": "string" }
        },
        "required": ["name", "score", "comment"]
      }
    },
    "corrections": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "original": { "type": "string" },
          "corrected": { "type": "string" },
          "reason": { "type": "string" }
        },
        "required": ["original", "corrected", "reason"]
      }
    },
    "corrected_text": { "type": "string" }
  },
  "required": ["score", "summary", "criteria", "corrections", "corrected_text"]
}"""
    }
}
