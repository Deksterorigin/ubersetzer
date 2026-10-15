package app.deutschlern.domain.model.dialog

import kotlinx.serialization.Serializable

object DialogStatus {
    const val RECORDING = "RECORDING"
    const val COMPLETE = "COMPLETE"
    const val INTERRUPTED = "INTERRUPTED"
}

data class Dialog(
    val id: Long = 0,
    val title: String,
    val startedAt: Long,
    val durationMs: Long,
    val localeTag: String = "de-DE",
    val segmentCount: Int = 0,
    val summary: DialogSummary? = null,
    val summaryProviderModel: String? = null,
    val summaryUpdatedAt: Long? = null,
    val status: String = DialogStatus.COMPLETE
)

data class DialogSegment(
    val id: Long = 0,
    val dialogId: Long,
    val orderIndex: Int,
    val text: String,
    val speakerLabel: String? = null,
    val startOffsetMs: Long
)

@Serializable
data class DialogSummary(
    val topic: String,
    val summaryDe: String,
    val summaryPartner: String,
    val keyPoints: List<String> = emptyList(),
    val keyVocabulary: List<KeyVocabularyItem> = emptyList()
)

@Serializable
data class KeyVocabularyItem(
    val german: String,
    val translation: String,
    val contextSentence: String? = null
)

enum class DialogTaskType {
    MCQ,
    GAP_FILL,
    OPEN_RESPONSE;

    companion object {
        fun fromString(value: String): DialogTaskType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MCQ
        }
    }
}

data class DialogTask(
    val id: Long = 0,
    val dialogId: Long,
    val type: DialogTaskType,
    val payloadJson: String,
    val resultJson: String? = null,
    val scorePercent: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Serializable
data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanationPartner: String
)

@Serializable
data class QuizTaskPayload(
    val questions: List<QuizQuestion>
)

@Serializable
data class QuizTaskResult(
    val answers: List<Int> // selected option index for each question (-1 if unanswered)
)

@Serializable
data class GapFillExercise(
    val sentenceWithGap: String,
    val expectedAnswer: String,
    val alternatives: List<String> = emptyList(),
    val hintPartner: String? = null
)

@Serializable
data class GapFillTaskPayload(
    val exercises: List<GapFillExercise>
)

@Serializable
data class GapFillTaskResult(
    val userAnswers: List<String>
)

@Serializable
data class OpenQuestion(
    val promptDe: String,
    val promptPartner: String
)

@Serializable
data class OpenTaskPayload(
    val questions: List<OpenQuestion>
)

@Serializable
data class OpenEvaluation(
    val scorePercent: Int,
    val corrections: List<String> = emptyList(),
    val improvedVersion: String,
    val feedbackPartner: String
)

@Serializable
data class OpenTaskResult(
    val answers: List<String>,
    val evaluations: List<OpenEvaluation>
)
