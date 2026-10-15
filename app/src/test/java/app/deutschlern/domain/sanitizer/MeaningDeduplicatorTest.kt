package app.deutschlern.domain.sanitizer

import app.deutschlern.domain.model.Meaning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeaningDeduplicatorTest {

    @Test
    fun cleanNote_stripsParenthesesAndTrims() {
        assertEquals("здание", MeaningDeduplicator.cleanNote("(здание)"))
        assertEquals("дверной замок", MeaningDeduplicator.cleanNote("  (дверной замок)  "))
        assertEquals("пояснение", MeaningDeduplicator.cleanNote("пояснение"))
        assertNull(MeaningDeduplicator.cleanNote(null))
        assertNull(MeaningDeduplicator.cleanNote("   "))
    }

    @Test
    fun cleanNote_filtersFrequencyStatements() {
        assertNull(MeaningDeduplicator.cleanNote("most common meaning"))
        assertNull(MeaningDeduplicator.cleanNote("основное значение"))
        assertNull(MeaningDeduplicator.cleanNote("самое частое"))
    }

    @Test
    fun cleanNote_capsAtEightWords() {
        val longNote = "one two three four five six seven eight nine ten eleven twelve"
        val cleaned = MeaningDeduplicator.cleanNote(longNote)
        assertEquals("one two three four five six seven eight...", cleaned)
    }

    @Test
    fun deduplicateMeanings_identicalTranslations_dedupedToOne() {
        val meanings = listOf(
            Meaning(translation = "упоминать", note = "основное"),
            Meaning(translation = "упоминать", note = "вскользь")
        )
        val result = MeaningDeduplicator.deduplicateMeanings(meanings)
        assertEquals(1, result.size)
        assertEquals("упоминать", result[0].translation)
    }

    @Test
    fun deduplicateMeanings_parentheticalVariants_dedupedToOne() {
        val meanings = listOf(
            Meaning(translation = "упоминать"),
            Meaning(translation = "упоминать (в разговоре)")
        )
        val result = MeaningDeduplicator.deduplicateMeanings(meanings)
        assertEquals(1, result.size)
        assertEquals("упоминать", result[0].translation)
    }

    @Test
    fun deduplicateMeanings_distinctMeanings_preserved() {
        val meanings = listOf(
            Meaning(translation = "замок", note = "дворец"),
            Meaning(translation = "замок", note = "дверной")
        )
        // Here translations are same, but let's test distinctly different translations
        val distinct = listOf(
            Meaning(translation = "дворец"),
            Meaning(translation = "замок")
        )
        val result = MeaningDeduplicator.deduplicateMeanings(distinct)
        assertEquals(2, result.size)
    }

    @Test
    fun remapSelectedMeaningIndex_whenIndexValidAndPreserved_returnsSameIndex() {
        val oldMeanings = listOf(
            Meaning(translation = "дворец"),
            Meaning(translation = "замок")
        )
        val newMeanings = listOf(
            Meaning(translation = "дворец"),
            Meaning(translation = "замок")
        )
        val remapped = MeaningDeduplicator.remapSelectedMeaningIndex(
            oldMeanings = oldMeanings,
            newMeanings = newMeanings,
            oldIndex = 1
        )
        assertEquals(1, remapped)
    }

    @Test
    fun remapSelectedMeaningIndex_whenPrecedingDuplicateRemoved_remapsToNewIndex() {
        val oldMeanings = listOf(
            Meaning(translation = "упоминать"),
            Meaning(translation = "упоминать (вскользь)"),
            Meaning(translation = "касаться")
        )
        val newMeanings = listOf(
            Meaning(translation = "упоминать"),
            Meaning(translation = "касаться")
        )
        // oldIndex = 2 ("касаться") should become newIndex = 1
        val remapped = MeaningDeduplicator.remapSelectedMeaningIndex(
            oldMeanings = oldMeanings,
            newMeanings = newMeanings,
            oldIndex = 2
        )
        assertEquals(1, remapped)
    }

    @Test
    fun remapSelectedMeaningIndex_whenOldIndexNull_returnsNull() {
        val oldMeanings = listOf(Meaning(translation = "слово"))
        val newMeanings = listOf(Meaning(translation = "слово"))
        val remapped = MeaningDeduplicator.remapSelectedMeaningIndex(
            oldMeanings = oldMeanings,
            newMeanings = newMeanings,
            oldIndex = null
        )
        assertNull(remapped)
    }

    @Test
    fun remapSelectedMeaningIndex_whenMeaningNotFound_resetsToNull() {
        val oldMeanings = listOf(
            Meaning(translation = "старое"),
            Meaning(translation = "удаленное")
        )
        val newMeanings = listOf(
            Meaning(translation = "новое 1"),
            Meaning(translation = "новое 2")
        )
        val remapped = MeaningDeduplicator.remapSelectedMeaningIndex(
            oldMeanings = oldMeanings,
            newMeanings = newMeanings,
            oldIndex = 1
        )
        assertNull(remapped)
    }
}
