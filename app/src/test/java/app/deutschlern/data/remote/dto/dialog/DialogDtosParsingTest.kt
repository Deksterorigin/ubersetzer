package app.deutschlern.data.remote.dto.dialog

import app.deutschlern.domain.parser.StructuredJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogDtosParsingTest {

    @Test
    fun parseSummaryResponse_success() {
        val json = """
        {
          "topic": "Reiseplanung nach Berlin",
          "summary_de": "Zwei Freunde planen eine gemeinsame Reise nach Berlin für das Wochenende.",
          "summary_partner": "Двое друзей планируют совместную поездку в Берлин на выходные.",
          "key_points": [
            "Sie fahren am Freitagabend mit dem Zug.",
            "Das Hotel liegt in der Nähe des Alexanderplatzes."
          ],
          "key_vocabulary": [
            {
              "german": "die Reise",
              "translation": "поездка",
              "context_sentence": "Wir freuen uns auf die Reise."
            }
          ]
        }
        """.trimIndent()

        val result = StructuredJsonParser.parse(
            rawText = json,
            serializer = DialogSummaryResponseDto.serializer(),
            providerName = "test"
        )

        assertTrue(result.isSuccess)
        val dto = result.getOrThrow()
        assertEquals("Reiseplanung nach Berlin", dto.topic)
        assertEquals(2, dto.keyPoints.size)
        assertEquals(1, dto.keyVocabulary.size)
        assertEquals("die Reise", dto.keyVocabulary[0].german)
    }

    @Test
    fun parseQuizResponse_withMarkdownFences_success() {
        val jsonWithFences = """
        ```json
        {
          "questions": [
            {
              "question": "Wann fahren die Freunde nach Berlin?",
              "options": [
                "Am Freitagmorgen",
                "Am Freitagabend",
                "Am Samstag",
                "Am Sonntag"
              ],
              "correct_index": 1,
              "explanation_partner": "Они договорились поехать в пятницу вечером после работы."
            }
          ]
        }
        ```
        """.trimIndent()

        val result = StructuredJsonParser.parse(
            rawText = jsonWithFences,
            serializer = DialogQuizResponseDto.serializer(),
            providerName = "test"
        )

        assertTrue(result.isSuccess)
        val dto = result.getOrThrow()
        assertEquals(1, dto.questions.size)
        assertEquals(1, dto.questions[0].correctIndex)
        assertEquals(4, dto.questions[0].options.size)
    }

    @Test
    fun parseGapFillResponse_success() {
        val json = """
        {
          "exercises": [
            {
              "sentence_with_gap": "Wir treffen uns am ___ vor dem Hauptbahnhof.",
              "expected_answer": "Freitag",
              "alternatives": ["Freitagabend"],
              "hint_partner": "день недели (пятница)"
            }
          ]
        }
        """.trimIndent()

        val result = StructuredJsonParser.parse(
            rawText = json,
            serializer = DialogGapFillResponseDto.serializer(),
            providerName = "test"
        )

        assertTrue(result.isSuccess)
        val dto = result.getOrThrow()
        assertEquals(1, dto.exercises.size)
        assertEquals("Freitag", dto.exercises[0].expectedAnswer)
    }

    @Test
    fun parseOpenEvaluationResponse_success() {
        val json = """
        {
          "score_percent": 85,
          "corrections": [
            "Es heißt 'Ich freue mich auf', nicht 'Ich freue mich über' für zukünftige Ereignisse."
          ],
          "improved_version": "Ich freue mich schon sehr auf unsere Reise.",
          "feedback_partner": "Отличный ответ! Вы хорошо передали мысль, обратите внимание на управление глагола freuen."
        }
        """.trimIndent()

        val result = StructuredJsonParser.parse(
            rawText = json,
            serializer = OpenAnswerEvaluationResponseDto.serializer(),
            providerName = "test"
        )

        assertTrue(result.isSuccess)
        val dto = result.getOrThrow()
        assertEquals(85, dto.scorePercent)
        assertEquals(1, dto.corrections.size)
        assertNotNull(dto.improvedVersion)
    }
}
