package app.deutschlern.domain.trainer

import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.VerbForms
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseFactoryTest {

    private fun word(
        id: Long,
        lemma: String,
        pos: String,
        translation: String = "перевод $id",
        article: String? = null,
        plural: String? = null,
        verbForms: VerbForms? = null,
        exampleDe: String = "",
        status: WordStatus = WordStatus.LEARNING,
        isDifficult: Boolean = false
    ) = WordEntry(
        id = id,
        lemma = lemma,
        article = article,
        pos = pos,
        plural = plural,
        verbForms = verbForms,
        meanings = listOf(
            Meaning(
                translation = translation,
                exampleDe = exampleDe,
                exampleRu = if (exampleDe.isEmpty()) "" else "пример"
            )
        ),
        sessionId = 1,
        status = status,
        isDifficult = isDifficult
    )

    private fun noun(id: Long = 1, plural: String? = "Häuser", example: String = "") =
        word(id, "Haus", "noun", "дом", article = "das", plural = plural, exampleDe = example)

    private fun verb(
        id: Long = 2,
        lemma: String = "machen",
        auxiliary: String = "haben",
        praeteritum: String = "machte",
        partizip2: String = "gemacht",
        example: String = ""
    ) = word(
        id, lemma, "verb", "делать",
        verbForms = VerbForms("macht", praeteritum, partizip2, auxiliary, false),
        exampleDe = example
    )

    private val factory = ExerciseFactory(Random(1))

    // ---- canBuild ----

    @Test
    fun articlesCanBuildForNounOnly() {
        assertTrue(factory.canBuild(noun(), TrainerMode.ARTICLES))
        assertFalse(factory.canBuild(verb(), TrainerMode.ARTICLES))
    }

    @Test
    fun formsCanBuildWithPluralOrVerbForms() {
        assertTrue(factory.canBuild(noun(), TrainerMode.FORMS))
        assertTrue(factory.canBuild(verb(), TrainerMode.FORMS))
    }

    @Test
    fun formsIgnoreDashPlural() {
        assertFalse(factory.canBuild(noun(plural = "-"), TrainerMode.FORMS))
        assertFalse(factory.canBuild(noun(plural = null), TrainerMode.FORMS))
    }

    @Test
    fun clozeCanBuildOnlyWhenFormFoundInSentence() {
        val w = verb(lemma = "besichtigen", example = "Wir haben das Schloss besichtigt.")
        assertTrue(factory.canBuild(w, TrainerMode.CLOZE))
        assertFalse(factory.canBuild(verb(), TrainerMode.CLOZE))
    }

    // ---- cloze ----

    @Test
    fun clozeFindsInflectedFormAndBuildsGap() {
        val w = verb(lemma = "besichtigen", example = "Wir haben das Schloss besichtigt.")
        val ex = factory.build(w, TrainerMode.CLOZE, emptyList()) as Exercise.Typing
        assertEquals(TypingKind.CLOZE, ex.kind)
        assertEquals("besichtigt", ex.expected)
        assertEquals("Wir haben das Schloss ${ExerciseFactory.GAP}.", ex.prompt)
        assertTrue(ex.nearMisses.contains("besichtigen"))
    }

    @Test
    fun clozeWorksForMultiWordLemma() {
        val w = word(3, "auf dem Laufenden", "phrase", exampleDe = "Er hält mich auf dem Laufenden.")
        assertTrue(factory.canBuild(w, TrainerMode.CLOZE))
        val ex = factory.build(w, TrainerMode.CLOZE, emptyList()) as Exercise.Typing
        assertEquals("auf dem Laufenden", ex.expected)
        assertEquals("Er hält mich ${ExerciseFactory.GAP}.", ex.prompt)
    }

    // ---- forms ----

    @Test
    fun perfektExpectedUsesSeinAuxiliary() {
        val w = verb(lemma = "gehen", auxiliary = "sein", praeteritum = "", partizip2 = "gegangen")
        val ex = factory.build(w, TrainerMode.FORMS, emptyList()) as Exercise.Typing
        assertEquals(TypingKind.PERFEKT, ex.kind)
        assertEquals("ist gegangen", ex.expected)
    }

    @Test
    fun perfektExpectedUsesHabenAuxiliary() {
        val w = verb(praeteritum = "")
        val ex = factory.build(w, TrainerMode.FORMS, emptyList()) as Exercise.Typing
        assertEquals("hat gemacht", ex.expected)
    }

    @Test
    fun pluralFormExpectedHasNoArticle() {
        val w = noun(plural = "die Häuser")
        val ex = factory.build(w, TrainerMode.FORMS, emptyList()) as Exercise.Typing
        assertEquals(TypingKind.PLURAL, ex.kind)
        assertEquals("Häuser", ex.expected)
    }

    // ---- choice ----

    private fun nounPool(from: Long, count: Int) =
        (0 until count).map { word(from + it, "Wort$it", "noun", "сущ $it", article = "der") }

    private fun verbPool(from: Long, count: Int) =
        (0 until count).map { word(from + it, "tun$it", "verb", "глаг $it") }

    @Test
    fun choiceHasCorrectAnswerAtCorrectIndexWithoutDuplicates() {
        val pool = nounPool(100, 6) + listOf(noun(id = 1))
        repeat(20) { seed ->
            val ex = ExerciseFactory(Random(seed)).build(noun(id = 1), TrainerMode.CHOICE, pool) as Exercise.Choice
            assertEquals("дом", ex.options[ex.correctIndex])
            assertEquals(ex.options.size, ex.options.toSet().size)
            assertEquals(4, ex.options.size)
        }
    }

    @Test
    fun choiceDistractorsExcludeDuplicateTranslations() {
        val pool = listOf(
            word(10, "A", "noun", "дом"),
            word(11, "B", "noun", "ДОМ"),
            word(12, "C", "noun", "кот"),
            word(13, "D", "noun", "кот")
        )
        val ex = factory.build(noun(), TrainerMode.CHOICE, pool) as Exercise.Choice
        assertEquals(setOf("дом", "кот"), ex.options.toSet())
        assertEquals(2, ex.options.size)
    }

    @Test
    fun choiceDistractorsPreferSamePartOfSpeech() {
        val pool = nounPool(100, 10) + verbPool(200, 10)
        repeat(10) { seed ->
            val ex = ExerciseFactory(Random(seed)).build(noun(), TrainerMode.CHOICE, pool) as Exercise.Choice
            val distractors = ex.options.filterIndexed { i, _ -> i != ex.correctIndex }
            assertTrue(distractors.all { it.startsWith("сущ") })
        }
    }

    @Test
    fun choiceReturnsNullWithoutOtherWords() {
        assertNull(factory.build(noun(), TrainerMode.CHOICE, emptyList()))
        assertNull(factory.build(noun(), TrainerMode.CHOICE, listOf(noun())))
    }

    // ---- mixed ----

    private fun formatOf(ex: Exercise): String = when (ex) {
        is Exercise.Article -> "ARTICLE"
        is Exercise.Choice -> "CHOICE"
        is Exercise.Speaking -> "SPEAKING"
        is Exercise.Typing -> when (ex.kind) {
            TypingKind.PLURAL, TypingKind.PRAETERITUM, TypingKind.PERFEKT -> "FORMS"
            else -> ex.kind.name
        }
    }

    @Test
    fun mixedGivesNewWordsChoice() {
        val newWords = (1L..5L).map { word(it, "Neu$it", "noun", "новое $it", article = "die", status = WordStatus.NEW) }
        val pool = newWords + nounPool(100, 5)
        val result = ExerciseFactory(Random(3)).build(newWords, TrainerMode.MIXED, pool)
        assertEquals(5, result.size)
        assertTrue(result.all { it is Exercise.Choice })
    }

    @Test
    fun mixedNeverRepeatsFormatForPracticedWords() {
        val words = (1L..20L).map {
            word(
                it, "Ding$it", "noun", "вещь $it", article = "das", plural = "Dinge$it",
                exampleDe = "Das Ding$it liegt hier.", status = WordStatus.LEARNING
            )
        }
        repeat(10) { seed ->
            val result = ExerciseFactory(Random(seed)).build(words, TrainerMode.MIXED, words)
            assertEquals(20, result.size)
            val formats = result.map(::formatOf)
            for (i in 1 until formats.size) {
                assertTrue("seed=$seed: ${formats[i - 1]} twice at $i: $formats", formats[i] != formats[i - 1])
            }
        }
    }

    @Test
    fun mixedFallsBackToTypingWhenChoiceImpossible() {
        val lonely = word(1, "Neu", "noun", "новое", article = "die", status = WordStatus.NEW)
        val result = factory.build(listOf(lonely), TrainerMode.MIXED, listOf(lonely))
        assertEquals(1, result.size)
        val ex = result.single()
        assertTrue(ex is Exercise.Typing)
        assertEquals(TypingKind.TRANSLATE_TO_GERMAN, (ex as Exercise.Typing).kind)
        assertNotNull(ex.requiredArticle)
    }
}
