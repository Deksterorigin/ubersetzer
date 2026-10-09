package app.deutschlern.data.remote.dto

import app.deutschlern.di.NetworkModule
import app.deutschlern.domain.model.toDomain
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTranslationJsonParsingTest {

    private val json = NetworkModule.provideJson()

    @Test
    fun parseCompleteWordResponse_success() {
        val jsonString = """
        {
          "lemma": "Schloss",
          "article": "das",
          "pos": "noun",
          "plural": "Schlösser",
          "genitive": "Schlosses",
          "verb_forms": null,
          "government": null,
          "comparative": null,
          "superlative": null,
          "meanings": [
            {
              "translation": "замок (дворец)",
              "note": "архитектура",
              "register": "gehoben",
              "example_de": "Das alte Schloss steht auf einem bewaldeten Hügel.",
              "example_ru": "Старый замок стоит на лесистом холме.",
              "best_match": false
            },
            {
              "translation": "замок (дверной)",
              "note": "механизм",
              "register": null,
              "example_de": "Er steckte den Schlüssel vorsichtig ins Schloss.",
              "example_ru": "Он осторожно вставил ключ в замок.",
              "best_match": true
            }
          ],
          "synonyms": ["Burg", "Palast"],
          "common_collocations": [
            {
              "de": "ins Schloss fallen",
              "ru": "захлопнуться (о двери)"
            }
          ]
        }
        """.trimIndent()

        val dto = json.decodeFromString<WordTranslationDto>(jsonString)

        assertEquals("Schloss", dto.lemma)
        assertEquals("das", dto.article)
        assertEquals("noun", dto.pos)
        assertEquals("Schlösser", dto.plural)
        assertEquals("Schlosses", dto.genitive)
        assertNull(dto.verb_forms)
        assertEquals(2, dto.meanings?.size)

        val firstMeaning = dto.meanings!![0]
        assertEquals("замок (дворец)", firstMeaning.translation)
        assertEquals("архитектура", firstMeaning.note)
        assertEquals("gehoben", firstMeaning.register)
        assertEquals(false, firstMeaning.best_match)

        val secondMeaning = dto.meanings!![1]
        assertEquals("замок (дверной)", secondMeaning.translation)
        assertEquals(true, secondMeaning.best_match)

        assertEquals(listOf("Burg", "Palast"), dto.synonyms)
        assertEquals(1, dto.common_collocations?.size)
        assertEquals("ins Schloss fallen", dto.common_collocations!![0].de)
        assertEquals("захлопнуться (о двери)", dto.common_collocations!![0].ru)
    }

    @Test
    fun parseVerbResponse_withoutArticles_success() {
        val jsonString = """
        {
          "lemma": "aufstehen",
          "article": null,
          "pos": "verb",
          "plural": null,
          "genitive": null,
          "verb_forms": {
            "praesens_er": "steht auf",
            "praeteritum": "stand auf",
            "partizip2": "aufgestanden",
            "auxiliary": "sein",
            "separable": true,
            "prefix": "auf"
          },
          "government": null,
          "meanings": [
            {
              "translation": "вставать, подниматься",
              "note": "с постели",
              "example_de": "Er muss jeden Morgen früh aufstehen.",
              "example_ru": "Каждое утро он должен вставать рано.",
              "best_match": false
            }
          ],
          "synonyms": ["erwachen", "sich erheben"],
          "common_collocations": [
            {
              "de": "früh aufstehen",
              "ru": "рано вставать"
            }
          ]
        }
        """.trimIndent()

        val dto = json.decodeFromString<WordTranslationDto>(jsonString)

        assertEquals("aufstehen", dto.lemma)
        assertNull(dto.article)
        assertEquals("verb", dto.pos)
        assertNull(dto.plural)
        assertNull(dto.genitive)

        val verbForms = dto.verb_forms
        assertNotNull(verbForms)
        assertEquals("steht auf", verbForms?.praesens_er)
        assertEquals("stand auf", verbForms?.praeteritum)
        assertEquals("aufgestanden", verbForms?.partizip2)
        assertEquals("sein", verbForms?.auxiliary)
        assertTrue(verbForms?.separable == true)
        assertEquals("auf", verbForms?.prefix)
    }

    @Test
    fun parseNounResponse_withoutVerbForms_success() {
        val jsonString = """
        {
          "lemma": "Tisch",
          "article": "der",
          "pos": "noun",
          "plural": "Tische",
          "genitive": "Tisches",
          "meanings": [
            {
              "translation": "стол",
              "example_de": "Das Buch liegt auf dem Tisch.",
              "example_ru": "Книга лежит на столе."
            }
          ]
        }
        """.trimIndent()

        val dto = json.decodeFromString<WordTranslationDto>(jsonString)

        assertEquals("Tisch", dto.lemma)
        assertEquals("der", dto.article)
        assertEquals("noun", dto.pos)
        assertEquals("Tische", dto.plural)
        assertNull(dto.verb_forms)
        assertEquals(emptyList<String>(), dto.synonyms)
        assertEquals(emptyList<CollocationDto>(), dto.common_collocations)
    }

    @Test
    fun parseAdjectiveResponse_withDegreesAndGovernment() {
        val jsonString = """
        {
          "lemma": "stolz",
          "pos": "adjective",
          "comparative": "stolzer",
          "superlative": "am stolzesten",
          "government": "auf + Akk",
          "meanings": [
            {
              "translation": "гордый",
              "example_de": "Die Eltern sind sehr stolz auf ihre Tochter.",
              "example_ru": "Родители очень гордятся своей дочерью.",
              "best_match": true
            }
          ]
        }
        """.trimIndent()

        val dto = json.decodeFromString<WordTranslationDto>(jsonString)

        assertEquals("stolz", dto.lemma)
        assertEquals("adjective", dto.pos)
        assertEquals("stolzer", dto.comparative)
        assertEquals("am stolzesten", dto.superlative)
        assertEquals("auf + Akk", dto.government)
        assertNull(dto.article)
        assertNull(dto.plural)
    }

    @Test
    fun nullSafety_allOptionalFieldsMissing_usesDefaultsWithoutException() {
        val minimalJson = """
        {
          "lemma": "Buch",
          "pos": "noun",
          "meanings": []
        }
        """.trimIndent()

        val dto = json.decodeFromString<WordTranslationDto>(minimalJson)

        assertEquals("Buch", dto.lemma)
        assertNull(dto.article)
        assertEquals("noun", dto.pos)
        assertNull(dto.plural)
        assertNull(dto.genitive)
        assertNull(dto.verb_forms)
        assertNull(dto.government)
        assertNull(dto.comparative)
        assertNull(dto.superlative)
        assertTrue(dto.meanings.orEmpty().isEmpty())
        assertTrue(dto.synonyms.orEmpty().isEmpty())
        assertTrue(dto.common_collocations.orEmpty().isEmpty())
    }

    @Test
    fun nullSafety_explicitNullFieldsAndCollections_deserializesSafely() {
        val jsonWithExplicitNulls = """
        {
          "lemma": "Buch",
          "article": null,
          "pos": "noun",
          "plural": null,
          "genitive": null,
          "verb_forms": {
            "praesens_er": null,
            "praeteritum": null,
            "partizip2": null,
            "auxiliary": null,
            "separable": null,
            "prefix": null
          },
          "government": null,
          "comparative": null,
          "superlative": null,
          "meanings": null,
          "synonyms": null,
          "common_collocations": null
        }
        """.trimIndent()

        val dto = json.decodeFromString<WordTranslationDto>(jsonWithExplicitNulls)
        val domain = dto.toDomain()

        assertEquals("Buch", domain.lemma)
        assertTrue(domain.meanings.isEmpty())
        assertTrue(domain.synonyms.isEmpty())
        assertTrue(domain.collocations.isEmpty())
        assertEquals("", domain.verbForms?.praesensEr)
        assertFalse(domain.verbForms?.separable ?: true)
    }

    @Test
    fun malformedJson_throwsSerializationException() {
        val malformedJson = """{"lemma": "Schloss", "meanings": ["""

        assertThrows(SerializationException::class.java) {
            json.decodeFromString<WordTranslationDto>(malformedJson)
        }
    }
}
