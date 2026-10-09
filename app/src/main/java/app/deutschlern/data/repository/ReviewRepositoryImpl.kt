package app.deutschlern.data.repository

import app.deutschlern.data.local.dao.ReviewDao
import app.deutschlern.data.local.dao.SessionDao
import app.deutschlern.data.local.dao.SessionWordDao
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.data.local.entity.ReviewStateEntity
import app.deutschlern.data.local.mapper.WordEntryMapper
import app.deutschlern.di.IoDispatcher
import app.deutschlern.domain.model.ReviewCard
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.repository.ReviewRepository
import app.deutschlern.domain.sm2.Sm2Grade
import app.deutschlern.domain.sm2.Sm2Scheduler
import app.deutschlern.domain.sm2.Sm2State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewRepositoryImpl @Inject constructor(
    private val wordDao: WordDao,
    private val reviewDao: ReviewDao,
    private val sessionDao: SessionDao,
    private val sessionWordDao: SessionWordDao,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ReviewRepository {

    override fun getDueReviewCards(now: Long): Flow<List<ReviewCard>> {
        return combine(
            wordDao.getAllWords(),
            reviewDao.getAllReviewStates()
        ) { wordEntities, reviewEntities ->
            val reviewMap = reviewEntities.associateBy { it.wordId }

            wordEntities.mapNotNull { wordEntity ->
                val word = WordEntryMapper.entityToDomain(wordEntity, json)
                val sm2State = reviewMap[word.id]?.toDomain() ?: Sm2State(dueAt = 0L)
                ReviewCard(word = word, sm2State = sm2State)
            }
            .filter { it.sm2State.dueAt <= now }
            .sortedWith(
                compareByDescending<ReviewCard> { it.word.timesTranslated }
                    .thenBy { it.sm2State.easeFactor }
                    .thenBy { it.sm2State.dueAt }
            )
        }
    }

    override fun getLastLessonReviewCards(): Flow<List<ReviewCard>> {
        return combine(
            sessionDao.getAllSessions(),
            wordDao.getAllWords(),
            sessionWordDao.getAll(),
            reviewDao.getAllReviewStates()
        ) { sessions, words, sessionWords, reviewStates ->
            val lastSession = sessions.maxByOrNull { it.date }
            if (lastSession == null) return@combine emptyList<ReviewCard>()

            val wordMap = words.associateBy { it.id }
            val reviewMap = reviewStates.associateBy { it.wordId }
            val wordsInSessionIds = sessionWords
                .filter { it.sessionId == lastSession.id }
                .map { it.wordId }
                .toSet()

            wordsInSessionIds.mapNotNull { wordId ->
                val entity = wordMap[wordId] ?: return@mapNotNull null
                val word = WordEntryMapper.entityToDomain(entity, json)
                val sm2State = reviewMap[word.id]?.toDomain() ?: Sm2State(dueAt = 0L)
                ReviewCard(word = word, sm2State = sm2State)
            }
            .sortedWith(
                compareByDescending<ReviewCard> { it.word.timesTranslated }
                    .thenBy { it.sm2State.easeFactor }
            )
        }
    }

    override suspend fun reviewCard(
        wordId: Long,
        grade: Sm2Grade,
        now: Long
    ): ReviewCard = withContext(ioDispatcher) {
        val wordEntity = wordDao.getById(wordId)
            ?: throw IllegalArgumentException("Word with id $wordId not found")
        val currentEntity = reviewDao.getByWordId(wordId)
        val currentState = currentEntity?.toDomain() ?: Sm2State()

        val nextState = Sm2Scheduler.calculateNextState(
            currentState = currentState,
            grade = grade,
            now = now
        )

        reviewDao.insertOrReplace(
            ReviewStateEntity(
                wordId = wordId,
                easeFactor = nextState.easeFactor,
                intervalDays = nextState.intervalDays,
                repetitions = nextState.repetitions,
                dueAt = nextState.dueAt
            )
        )

        // Update word status according to repetition count
        val newStatus = when {
            nextState.repetitions >= 4 -> WordStatus.KNOWN
            nextState.repetitions > 0 -> WordStatus.LEARNING
            else -> WordStatus.NEW
        }
        wordDao.updateStatus(wordId, newStatus.name)

        val updatedWord = WordEntryMapper.entityToDomain(
            wordEntity.copy(status = newStatus.name),
            json
        )

        ReviewCard(word = updatedWord, sm2State = nextState)
    }

    override fun getDueCardsCount(now: Long): Flow<Int> {
        return getDueReviewCards(now).map { it.size }
    }

    private fun ReviewStateEntity.toDomain(): Sm2State {
        return Sm2State(
            easeFactor = easeFactor,
            intervalDays = intervalDays,
            repetitions = repetitions,
            dueAt = dueAt
        )
    }
}
