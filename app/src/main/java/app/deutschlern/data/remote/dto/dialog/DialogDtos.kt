package app.deutschlern.data.remote.dto.dialog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DialogSummaryResponseDto(
    val topic: String = "",
    @SerialName("summary_de") val summaryDe: String = "",
    @SerialName("summary_partner") val summaryPartner: String = "",
    @SerialName("key_points") val keyPoints: List<String> = emptyList(),
    @SerialName("key_vocabulary") val keyVocabulary: List<KeyVocabItemDto> = emptyList()
)

@Serializable
data class KeyVocabItemDto(
    val german: String = "",
    val translation: String = "",
    @SerialName("context_sentence") val contextSentence: String? = null
)

@Serializable
data class DialogQuizResponseDto(
    val questions: List<QuizQuestionDto> = emptyList()
)

@Serializable
data class QuizQuestionDto(
    val question: String = "",
    val options: List<String> = emptyList(),
    @SerialName("correct_index") val correctIndex: Int = 0,
    @SerialName("explanation_partner") val explanationPartner: String = ""
)

@Serializable
data class DialogGapFillResponseDto(
    val exercises: List<GapFillExerciseDto> = emptyList()
)

@Serializable
data class GapFillExerciseDto(
    @SerialName("sentence_with_gap") val sentenceWithGap: String = "",
    @SerialName("expected_answer") val expectedAnswer: String = "",
    val alternatives: List<String> = emptyList(),
    @SerialName("hint_partner") val hintPartner: String? = null
)

@Serializable
data class DialogOpenQuestionsResponseDto(
    val questions: List<OpenQuestionDto> = emptyList()
)

@Serializable
data class OpenQuestionDto(
    @SerialName("prompt_de") val promptDe: String = "",
    @SerialName("prompt_partner") val promptPartner: String = ""
)

@Serializable
data class OpenAnswerEvaluationResponseDto(
    @SerialName("score_percent") val scorePercent: Int = 0,
    val corrections: List<String> = emptyList(),
    @SerialName("improved_version") val improvedVersion: String = "",
    @SerialName("feedback_partner") val feedbackPartner: String = ""
)
