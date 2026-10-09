package app.deutschlern.domain.sm2

import kotlin.math.roundToInt

enum class Sm2Grade(val score: Int) {
    AGAIN(1),
    HARD(3),
    GOOD(4),
    EASY(5)
}

data class Sm2State(
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val dueAt: Long = 0L
)

object Sm2Scheduler {

    const val MIN_EASE_FACTOR = 1.3
    const val MAX_EASE_FACTOR = 3.0
    const val INITIAL_EASE_FACTOR = 2.5
    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * Calculates the next SM-2 state based on the selected rating.
     * Note: Conditions on repetitions (n == 0, n == 1) strictly use the value BEFORE incrementing.
     */
    fun calculateNextState(
        currentState: Sm2State,
        grade: Sm2Grade,
        now: Long = System.currentTimeMillis()
    ): Sm2State {
        val n = currentState.repetitions
        val i = currentState.intervalDays
        val ef = currentState.easeFactor.coerceIn(MIN_EASE_FACTOR, MAX_EASE_FACTOR)

        val nextRepetitions: Int
        val nextInterval: Int
        val nextEf: Double

        when (grade) {
            Sm2Grade.AGAIN -> {
                nextRepetitions = 0
                nextInterval = 1
                nextEf = (ef - 0.20).coerceIn(MIN_EASE_FACTOR, MAX_EASE_FACTOR)
            }
            Sm2Grade.HARD -> {
                nextRepetitions = n + 1
                nextInterval = if (n == 0) 1 else maxOf(1, (i * 1.2).roundToInt())
                nextEf = (ef - 0.15).coerceIn(MIN_EASE_FACTOR, MAX_EASE_FACTOR)
            }
            Sm2Grade.GOOD -> {
                nextInterval = when (n) {
                    0 -> 1
                    1 -> 6
                    else -> maxOf(1, (i * ef).roundToInt())
                }
                nextRepetitions = n + 1
                nextEf = ef.coerceIn(MIN_EASE_FACTOR, MAX_EASE_FACTOR)
            }
            Sm2Grade.EASY -> {
                nextInterval = when (n) {
                    0 -> 2
                    1 -> 7
                    else -> maxOf(1, (i * ef * 1.3).roundToInt())
                }
                nextRepetitions = n + 1
                nextEf = (ef + 0.15).coerceIn(MIN_EASE_FACTOR, MAX_EASE_FACTOR)
            }
        }

        val nextDueAt = now + nextInterval * ONE_DAY_MS

        return Sm2State(
            easeFactor = nextEf,
            intervalDays = nextInterval,
            repetitions = nextRepetitions,
            dueAt = nextDueAt
        )
    }
}
