package app.deutschlern.domain.parser

import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.provider.ProviderError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RobustWordJsonParserTest {

    @Test
    fun parse_validDeRuWithCodeFences_succeeds() {
        val raw = """
            Here is the translation:
            ```json
            {
              "lemma": "Haus",
              "article": "das",
              "pos": "noun",
              "meanings": [
                {
                  "translation": "дом",
                  "note": "здание",
                  "example_de": "Ich gehe nach Hause.",
                  "example_ru": "Я иду домой."
                }
              ]
            }
            ```
            Hope this helps!
        """.trimIndent()

        val result = RobustWordJsonParser.parse(raw, TranslationDirection.DE_RU, "Groq")
        assertTrue(result.isSuccess)
        val word = result.getOrThrow()
        assertEquals("Haus", word.lemma)
        assertEquals("das", word.article)
        assertEquals("дом", word.meanings.first().translation)
    }

    @Test
    fun parse_latinInRussianTranslation_failsSanityCheck() {
        val raw = """
            {
              "lemma": "Haus",
              "article": "das",
              "pos": "noun",
              "meanings": [
                {
                  "translation": "house",
                  "example_de": "Ein Haus.",
                  "example_ru": "A house."
                }
              ]
            }
        """.trimIndent()

        val result = RobustWordJsonParser.parse(raw, TranslationDirection.DE_RU, "Groq")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ProviderError.MalformedJson)
    }

    @Test
    fun parse_cyrillicInGermanLemma_failsSanityCheck() {
        val raw = """
            {
              "lemma": "Хаус",
              "article": "das",
              "pos": "noun",
              "meanings": [
                {
                  "translation": "дом",
                  "example_de": "Ein Haus.",
                  "example_ru": "Дом."
                }
              ]
            }
        """.trimIndent()

        val result = RobustWordJsonParser.parse(raw, TranslationDirection.DE_RU, "Groq")
        assertTrue(result.isFailure)
    }

    @Test
    fun parse_truncatedJson_failsGracefully() {
        val raw = """{"lemma": "Haus", "meanings": ["""
        val result = RobustWordJsonParser.parse(raw, TranslationDirection.DE_RU, "Mistral")
        assertTrue(result.isFailure)
    }

    @Test
    fun extractJsonString_extractsCorrectSubstrings() {
        val raw = "Sure! Here is the JSON:\n{\"lemma\": \"test\", \"pos\": \"noun\"}\nDone!"
        val extracted = RobustWordJsonParser.extractJsonString(raw)
        assertEquals("{\"lemma\": \"test\", \"pos\": \"noun\"}", extracted)
    }
}
