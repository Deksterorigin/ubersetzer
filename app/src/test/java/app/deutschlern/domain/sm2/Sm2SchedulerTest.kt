package app.deutschlern.domain.sm2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2SchedulerTest {

    private val fixedNow = 1000000000L
    private val oneDayMs = 24 * 60 * 60 * 1000L

    @Test
    fun again_grade_resetsRepetitionsToZero_andSetsIntervalToOneDay() {
        val initial = Sm2State(
            easeFactor = 2.5,
            intervalDays = 15,
            repetitions = 3,
            dueAt = fixedNow
        )

        val next = Sm2Scheduler.calculateNextState(initial, Sm2Grade.AGAIN, fixedNow)

        assertEquals(0, next.repetitions)
        assertEquals(1, next.intervalDays)
        assertEquals(2.30, next.easeFactor, 0.001)
        assertEquals(fixedNow + oneDayMs, next.dueAt)
    }

    @Test
    fun hard_grade_concreteNumericSequence() {
        // Step 1: initial word (n=0, i=0, ef=2.5)
        val s0 = Sm2State(easeFactor = 2.5, intervalDays = 0, repetitions = 0)
        val s1 = Sm2Scheduler.calculateNextState(s0, Sm2Grade.HARD, fixedNow)
        // Before incrementing n==0 -> nextInterval = 1, nextRepetitions = 1, ef' = 2.35
        assertEquals(1, s1.repetitions)
        assertEquals(1, s1.intervalDays)
        assertEquals(2.35, s1.easeFactor, 0.001)

        // Step 2: second review Hard (n=1, i=1, ef=2.35)
        val s2 = Sm2Scheduler.calculateNextState(s1, Sm2Grade.HARD, fixedNow)
        // Before incrementing n==1 -> nextInterval = max(1, round(1 * 1.2)) = 1, n' = 2, ef' = 2.20
        assertEquals(2, s2.repetitions)
        assertEquals(1, s2.intervalDays)
        assertEquals(2.20, s2.easeFactor, 0.001)

        // Step 3: third review Hard with longer interval (say i=10, n=2, ef=2.20)
        val s2Extended = s2.copy(intervalDays = 10)
        val s3 = Sm2Scheduler.calculateNextState(s2Extended, Sm2Grade.HARD, fixedNow)
        // nextInterval = round(10 * 1.2) = 12, n' = 3, ef' = 2.05
        assertEquals(3, s3.repetitions)
        assertEquals(12, s3.intervalDays)
        assertEquals(2.05, s3.easeFactor, 0.001)
    }

    @Test
    fun good_grade_concreteNumericSequence() {
        // Step 1: n=0 -> interval = 1 day, n'=1, EF'=2.5
        val s0 = Sm2State(easeFactor = 2.5, intervalDays = 0, repetitions = 0)
        val s1 = Sm2Scheduler.calculateNextState(s0, Sm2Grade.GOOD, fixedNow)
        assertEquals(1, s1.repetitions)
        assertEquals(1, s1.intervalDays)
        assertEquals(2.50, s1.easeFactor, 0.001)

        // Step 2: n=1 -> interval = 6 days, n'=2, EF'=2.5
        val s2 = Sm2Scheduler.calculateNextState(s1, Sm2Grade.GOOD, fixedNow)
        assertEquals(2, s2.repetitions)
        assertEquals(6, s2.intervalDays)
        assertEquals(2.50, s2.easeFactor, 0.001)

        // Step 3: n=2 -> interval = round(6 * 2.5) = 15 days, n'=3, EF'=2.5
        val s3 = Sm2Scheduler.calculateNextState(s2, Sm2Grade.GOOD, fixedNow)
        assertEquals(3, s3.repetitions)
        assertEquals(15, s3.intervalDays)
        assertEquals(2.50, s3.easeFactor, 0.001)

        // Step 4: n=3 -> interval = round(15 * 2.5) = 38 days, n'=4, EF'=2.5
        val s4 = Sm2Scheduler.calculateNextState(s3, Sm2Grade.GOOD, fixedNow)
        assertEquals(4, s4.repetitions)
        assertEquals(38, s4.intervalDays)
        assertEquals(2.50, s4.easeFactor, 0.001)
    }

    @Test
    fun easy_grade_concreteNumericSequence() {
        // Step 1: n=0 -> interval = 2 days, n'=1, EF'=2.65
        val s0 = Sm2State(easeFactor = 2.5, intervalDays = 0, repetitions = 0)
        val s1 = Sm2Scheduler.calculateNextState(s0, Sm2Grade.EASY, fixedNow)
        assertEquals(1, s1.repetitions)
        assertEquals(2, s1.intervalDays)
        assertEquals(2.65, s1.easeFactor, 0.001)

        // Step 2: n=1 -> interval = 7 days, n'=2, EF'=2.80
        val s2 = Sm2Scheduler.calculateNextState(s1, Sm2Grade.EASY, fixedNow)
        assertEquals(2, s2.repetitions)
        assertEquals(7, s2.intervalDays)
        assertEquals(2.80, s2.easeFactor, 0.001)

        // Step 3: n=2 -> interval = round(7 * 2.80 * 1.3) = round(25.48) = 25 days, n'=3, EF'=2.95
        val s3 = Sm2Scheduler.calculateNextState(s2, Sm2Grade.EASY, fixedNow)
        assertEquals(3, s3.repetitions)
        assertEquals(25, s3.intervalDays)
        assertEquals(2.95, s3.easeFactor, 0.001)
    }

    @Test
    fun easeFactor_clampsAtMin1_3_andMax3_0() {
        // Testing floor clamping at 1.3
        var state = Sm2State(easeFactor = 1.4, intervalDays = 5, repetitions = 1)
        state = Sm2Scheduler.calculateNextState(state, Sm2Grade.AGAIN, fixedNow)
        assertEquals(1.30, state.easeFactor, 0.001)
        state = Sm2Scheduler.calculateNextState(state, Sm2Grade.AGAIN, fixedNow)
        assertEquals(1.30, state.easeFactor, 0.001) // Does not fall below 1.3

        // Testing ceiling clamping at 3.0
        var easyState = Sm2State(easeFactor = 2.95, intervalDays = 5, repetitions = 1)
        easyState = Sm2Scheduler.calculateNextState(easyState, Sm2Grade.EASY, fixedNow)
        assertEquals(3.00, easyState.easeFactor, 0.001) // Does not exceed 3.0
        easyState = Sm2Scheduler.calculateNextState(easyState, Sm2Grade.EASY, fixedNow)
        assertEquals(3.00, easyState.easeFactor, 0.001)
    }
}
