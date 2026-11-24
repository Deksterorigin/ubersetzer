package app.deutschlern.domain.exam

import app.deutschlern.data.remote.dto.exam.ExamEvaluationResponseDto
import app.deutschlern.domain.parser.StructuredJsonParser
import app.deutschlern.domain.usecase.EvaluateExamWritingUseCase.Companion.buildUserPrompt
import app.deutschlern.domain.usecase.EvaluateExamWritingUseCase.Companion.toDomain
import app.deutschlern.domain.usecase.ExamTaskDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateExamWritingParsingTest {

    @Test
    fun `fenced model output is parsed, clamped and cleaned`() {
        val raw = """
            Hier ist die Bewertung:
            ```json
            {"score": 140, "summary": "Хорошо", "criteria": [{"name": "Grammatik", "score": 70, "comment": "Порядок слов"}],
             "corrections": [{"original": "weil ich habe", "corrected": "weil ich ... habe", "reason": "глагол в конец"},
                             {"original": "gleich", "corrected": "gleich", "reason": ""}],
             "corrected_text": " Text ", "extra": 1}
            ```
        """.trimIndent()
        val dto = StructuredJsonParser.parse(raw, ExamEvaluationResponseDto.serializer(), "test").getOrThrow()
        val evaluation = dto.toDomain()

        assertEquals(100, evaluation.score)
        assertEquals(1, evaluation.criteria.size)
        assertEquals(1, evaluation.corrections.size)
        assertEquals("Text", evaluation.correctedText)
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val dto = StructuredJsonParser.parse("""{"score": 55}""", ExamEvaluationResponseDto.serializer(), "test").getOrThrow()
        val evaluation = dto.toDomain()
        assertEquals(55, evaluation.score)
        assertTrue(evaluation.corrections.isEmpty())
    }

    @Test
    fun `user prompt carries situation and all points`() {
        val prompt = buildUserPrompt(
            ExamTaskDescription(situation = "Lieferung zu spät", points = listOf("Grund", "Forderung")),
            "Sehr geehrte Damen und Herren"
        )
        assertTrue(prompt.contains("Lieferung zu spät"))
        assertTrue(prompt.contains("- Grund"))
        assertTrue(prompt.contains("- Forderung"))
        assertTrue(prompt.contains("<candidate_text>"))
    }
}
