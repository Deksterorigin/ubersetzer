package app.deutschlern.domain.trainer

import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.ReviewCard
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.sm2.Sm2State
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainerSessionPlannerTest {

    private val now = 1_000_000_000L
    private val farFuture = now + 100L * 24 * 60 * 60 * 1000

    private fun card(
        id: Long,
        status: WordStatus,
        repetitions: Int,
        dueAt: Long,
        ease: Double = 2.5,
        difficult: Boolean = false
    ) = ReviewCard(
        WordEntry(
            id = id, lemma = "W$id", pos = "noun", sessionId = 1, status = status, isDifficult = difficult,
            meanings = listOf(Meaning("m$id"))
        ),
        Sm2State(easeFactor = ease, intervalDays = repetitions, repetitions = repetitions, dueAt = dueAt)
    )

    private fun newCard(id: Long, difficult: Boolean = false) = card(id, WordStatus.NEW, 0, 0L, difficult = difficult)
    private fun easyCard(id: Long) = card(id, WordStatus.KNOWN, 5, farFuture, ease = 2.8)

    @Test
    fun respectsSize() {
        val cards = (1L..40L).map { easyCard(it) }
        assertEquals(15, TrainerSessionPlanner.select(cards, WordPool.SMART, now, Random(1)).size)
        assertEquals(7, TrainerSessionPlanner.select(cards, WordPool.SMART, now, Random(1), size = 7).size)
    }

    @Test
    fun returnsEverythingWhenFewerThanSize() {
        val cards = (1L..4L).map { easyCard(it) }
        assertEquals(4, TrainerSessionPlanner.select(cards, WordPool.SMART, now, Random(1)).size)
    }

    @Test
    fun difficultPoolReturnsOnlyDifficultWords() {
        val cards = (1L..10L).map { easyCard(it) } +
            (11L..14L).map { card(it, WordStatus.LEARNING, 2, now - 1, difficult = true) } +
            (15L..16L).map { newCard(it, difficult = true) } +
            (17L..20L).map { newCard(it) }
        val result = TrainerSessionPlanner.select(cards, WordPool.DIFFICULT, now, Random(2))
        assertEquals(6, result.size)
        assertTrue(result.all { it.isDifficult })
    }

    @Test
    fun atMostFiveNewWordsWhenEnoughPracticed() {
        val cards = (1L..20L).map { card(it, WordStatus.LEARNING, 3, now - 1) } + (100L..120L).map { newCard(it) }
        repeat(10) { seed ->
            val result = TrainerSessionPlanner.select(cards, WordPool.SMART, now, Random(seed))
            assertEquals(15, result.size)
            assertTrue(result.count { it.status == WordStatus.NEW } <= 5)
        }
    }

    @Test
    fun difficultAndDueWordsBeatFarFutureEasyOnes() {
        val difficult = (1L..5L).map { card(it, WordStatus.LEARNING, 3, farFuture, difficult = true) }
        val due = (6L..10L).map { card(it, WordStatus.LEARNING, 3, now - 1) }
        val easy = (11L..40L).map { easyCard(it) }
        repeat(10) { seed ->
            val result = TrainerSessionPlanner.select(difficult + due + easy, WordPool.SMART, now, Random(seed), size = 10)
            assertEquals((1L..10L).toSet(), result.map { it.id }.toSet())
        }
    }

    @Test
    fun topsUpWithNewWordsWhenFewPracticed() {
        val cards = (1L..2L).map { easyCard(it) } + (100L..119L).map { newCard(it) }
        val result = TrainerSessionPlanner.select(cards, WordPool.SMART, now, Random(1))
        assertEquals(15, result.size)
        assertTrue(result.map { it.id }.containsAll(listOf(1L, 2L)))
        assertEquals(13, result.count { it.status == WordStatus.NEW })
        assertEquals(result.size, result.map { it.id }.toSet().size)
    }
}
