package app.deutschlern.domain.exam

/** What the model is grading: a written exam text or the transcript of a spoken answer. */
enum class ExamResponseKind { WRITING, SPEAKING }

data class ExamEvaluation(
    val score: Int,
    val summary: String,
    val criteria: List<ExamCriterion>,
    val corrections: List<ExamCorrection>,
    val correctedText: String
)

data class ExamCriterion(val name: String, val score: Int?, val comment: String)

data class ExamCorrection(val original: String, val corrected: String, val reason: String)
