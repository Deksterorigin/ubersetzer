package app.deutschlern.domain.parser

import app.deutschlern.domain.model.TranslationDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RobustWordJsonParserMultilingualTest {

    @Test
    fun parse_validDeEn_succeeds() {
        val raw = """
            {
              "lemma": "Buch",
              "article": "das",
              "pos": "noun",
              "meanings": [
                {
                  "translation": "book",
                  "note": "printed work",
                  "example_de": "Ich lese ein Buch.",
                  "example_ru": "I am reading a book."
                }
              ]
            }
        """.trimIndent()

        val result = RobustWordJsonParser.parse(raw, TranslationDirection.DE_EN, "TestProvider")
        assertTrue(result.isSuccess)
        val word = result.getOrThrow()
        assertEquals("Buch", word.lemma)
        assertEquals("das", word.article)
        assertEquals("book", word.meanings.first().translation)
    }

    @Test
    fun parse_validDeUk_succeeds() {
        val raw = """
            {
              "lemma": "Sonne",
              "article": "die",
              "pos": "noun",
              "meanings": [
                {
                  "translation": "сонце",
                  "note": "зірка",
                  "example_de": "Die Sonne scheint heute hell.",
                  "example_ru": "Сонце сьогодні світить яскраво."
                }
              ]
            }
        """.trimIndent()

        val result = RobustWordJsonParser.parse(raw, TranslationDirection.DE_UK, "TestProvider")
        assertTrue(result.isSuccess)
        val word = result.getOrThrow()
        assertEquals("Sonne", word.lemma)
        assertEquals("сонце", word.meanings.first().translation)
    }

    @Test
    fun parse_validDeAr_succeeds() {
        val raw = """
            {
              "lemma": "Wasser",
              "article": "das",
              "pos": "noun",
              "meanings": [
                {
                  "translation": "ماء",
                  "note": "سائل",
                  "example_de": "Ich trinke ein Glas kaltes Wasser.",
                  "example_ru": "أشرب كوبا من الماء البارد."
                }
              ]
            }
        """.trimIndent()

        val result = RobustWordJsonParser.parse(raw, TranslationDirection.DE_AR, "TestProvider")
        assertTrue(result.isSuccess)
        val word = result.getOrThrow()
        assertEquals("Wasser", word.lemma)
        assertEquals("ماء", word.meanings.first().translation)
    }

    @Test
    fun parse_validDeFa_succeeds() {
        val raw = """
            {
              "lemma": "Freund",
              "article": "der",
              "pos": "noun",
              "meanings": [
                {
                  "translation": "دوست",
                  "note": "رفیق",
                  "example_de": "Er ist mein bester Freund.",
                  "example_ru": "او بهترین دوست من است."
                }
              ]
            }
        """.trimIndent()

        val result = RobustWordJsonParser.parse(raw, TranslationDirection.DE_FA, "TestProvider")
        assertTrue(result.isSuccess)
        val word = result.getOrThrow()
        assertEquals("Freund", word.lemma)
        assertEquals("دوست", word.meanings.first().translation)
    }

    @Test
    fun parse_validDeRo_succeeds() {
        val raw = """
            {
              "lemma": "Tisch",
              "article": "der",
              "pos": "noun",
              "meanings": [
                {
                  "translation": "masă",
                  "note": "mobilă",
                  "example_de": "Das Buch liegt auf dem Tisch.",
                  "example_ru": "Cartea este pe masă."
                }
              ]
            }
        """.trimIndent()

        val result = RobustWordJsonParser.parse(raw, TranslationDirection.DE_RO, "TestProvider")
        assertTrue(result.isSuccess)
        val word = result.getOrThrow()
        assertEquals("Tisch", word.lemma)
        assertEquals("masă", word.meanings.first().translation)
    }

    @Test
    fun parse_arabicPartnerWithNoArabicScript_failsSanityCheck() {
        val raw = """
            {
              "lemma": "Wasser",
              "article": "das",
              "pos": "noun",
              "meanings": [
                {
                  "translation": "water",
                  "example_de": "Ich trinke Wasser.",
                  "example_ru": "I drink water."
                }
              ]
            }
        """.trimIndent()

        val result = RobustWordJsonParser.parse(raw, TranslationDirection.DE_AR, "TestProvider")
        assertTrue(result.isFailure)
    }

    @Test
    fun parse_ukrainianPartnerWithNoCyrillicScript_failsSanityCheck() {
        val raw = """
            {
              "lemma": "Sonne",
              "article": "die",
              "pos": "noun",
              "meanings": [
                {
                  "translation": "sun",
                  "example_de": "Die Sonne scheint.",
                  "example_ru": "The sun is shining."
                }
              ]
            }
        """.trimIndent()

        val result = RobustWordJsonParser.parse(raw, TranslationDirection.DE_UK, "TestProvider")
        assertTrue(result.isFailure)
    }
}
