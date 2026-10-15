package app.deutschlern.data.local

import android.util.Log
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.sanitizer.PosNormalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RepairedArticleBackupRecord(
    val id: Long,
    val lemma: String,
    val pos: String,
    val oldArticle: String
)

@Serializable
data class RepairedPosBackupRecord(
    val id: Long,
    val lemma: String,
    val oldPos: String,
    val newPos: String,
    val partnerLanguage: String
)

@Singleton
class LegacyArticleRepairManager @Inject constructor(
    private val wordDao: WordDao,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "LegacyArticleRepair"

        /**
         * Mapping from non-canonical legacy POS strings to canonical values:
         * n, nomen, substantiv -> noun
         * adj, adjektiv -> adjective
         * adv -> adverb
         * adverbial_phrase -> phrase
         */
        fun getTargetPos(rawPos: String): String? {
            val trimmed = rawPos.trim().lowercase()
            return when (trimmed) {
                "n", "nomen", "substantiv" -> PosNormalizer.POS_NOUN
                "adj", "adjektiv" -> PosNormalizer.POS_ADJECTIVE
                "adv" -> PosNormalizer.POS_ADVERB
                "adverbial_phrase" -> PosNormalizer.POS_PHRASE
                else -> null
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    /**
     * Executes one-time idempotent repair job on existing words in Room database.
     * Nulls article ONLY for words whose POS is in the explicit non-noun allowlist.
     * Unknown POS values are left untouched and logged.
     * Before nulling, writes a reversible JSON log to DataStore.
     * Guarded by hasRepairedNonNounArticles flag in DataStore.
     */
    suspend fun repairIfNeeded(): Int {
        val settings = settingsRepository.getSettings()
        if (settings.hasRepairedNonNounArticles) {
            Log.d(TAG, "Legacy article repair already executed. Skipping.")
            return 0
        }

        val allWords = wordDao.getAllWordsSync()
        val wordsToRepair = mutableListOf<RepairedArticleBackupRecord>()

        for (word in allWords) {
            val article = word.article
            if (article.isNullOrBlank()) continue

            val rawPos = word.pos

            if (PosNormalizer.isDefiniteNonNoun(rawPos)) {
                wordsToRepair.add(
                    RepairedArticleBackupRecord(
                        id = word.id,
                        lemma = word.lemma,
                        pos = rawPos,
                        oldArticle = article
                    )
                )
                // Null out the invalid article
                wordDao.update(word.copy(article = null))
            } else if (!PosNormalizer.isNoun(rawPos)) {
                Log.w(TAG, "Word id=${word.id} '${word.lemma}' has unknown POS '$rawPos' and article '$article'. Left untouched.")
            }
        }

        val backupJson = json.encodeToString(wordsToRepair)
        settingsRepository.markArticlesRepaired(backupJson)
        Log.i(TAG, "Repaired ${wordsToRepair.size} non-noun articles. Backup saved: $backupJson")

        return wordsToRepair.size
    }

    /**
     * Executes one-time idempotent POS normalization on existing words in Room database.
     * Normalizes legacy variants to canonical POS:
     *   n, nomen, substantiv -> noun
     *   adj, adjektiv -> adjective
     *   adv -> adverb
     *   adverbial_phrase -> phrase
     *
     * Pre-checks for collisions against (lemma, targetPos, partnerLanguage).
     * If a collision is found, skips that row without automatic merge and logs a warning.
     * Writes a reversible JSON log to DataStore.
     * Guarded by hasNormalizedPosInDb flag in DataStore.
     */
    suspend fun normalizePosIfNeeded(): Int {
        val settings = settingsRepository.getSettings()
        if (settings.hasNormalizedPosInDb) {
            Log.d(TAG, "POS normalization already executed. Skipping.")
            return 0
        }

        val allWords = wordDao.getAllWordsSync()
        val wordsToNormalize = mutableListOf<RepairedPosBackupRecord>()

        // Pre-index existing rows to detect potential (lemma, pos, partnerLanguage) collisions
        val existingIndex = allWords.associateBy {
            "${it.lemma.lowercase()}:${it.pos.lowercase()}:${it.partnerLanguage.lowercase()}"
        }

        for (word in allWords) {
            val targetPos = getTargetPos(word.pos) ?: continue
            if (word.pos.equals(targetPos, ignoreCase = true)) continue

            val partner = word.partnerLanguage.lowercase()
            val targetKey = "${word.lemma.lowercase()}:$targetPos:$partner"
            val collision = existingIndex[targetKey]

            if (collision != null && collision.id != word.id) {
                Log.w(
                    TAG,
                    "Collision detected for word id=${word.id} '${word.lemma}', pos '${word.pos}' -> '$targetPos', partner '$partner'. Existing row id=${collision.id}. Skipping normalization for this row."
                )
                continue
            }

            wordsToNormalize.add(
                RepairedPosBackupRecord(
                    id = word.id,
                    lemma = word.lemma,
                    oldPos = word.pos,
                    newPos = targetPos,
                    partnerLanguage = word.partnerLanguage
                )
            )

            wordDao.update(word.copy(pos = targetPos))
        }

        val backupJson = json.encodeToString(wordsToNormalize)
        settingsRepository.markPosNormalized(backupJson)
        Log.i(TAG, "Normalized ${wordsToNormalize.size} legacy POS entries. Backup saved: $backupJson")

        return wordsToNormalize.size
    }

    /**
     * Restores original POS values from the reversible backup JSON.
     * Re-enables normalization flag so it can be re-run if needed.
     */
    suspend fun restorePos(backupJson: String? = null): Int {
        val rawJson = backupJson ?: settingsRepository.getSettings().repairedPosBackupJson
        if (rawJson.isBlank()) return 0

        val records = runCatching {
            json.decodeFromString<List<RepairedPosBackupRecord>>(rawJson)
        }.getOrNull() ?: return 0

        var restoredCount = 0
        for (record in records) {
            val word = wordDao.getById(record.id)
            if (word != null) {
                wordDao.update(word.copy(pos = record.oldPos))
                restoredCount++
            }
        }

        settingsRepository.resetPosNormalizedFlag()
        Log.i(TAG, "Restored $restoredCount POS records from backup.")
        return restoredCount
    }

    /**
     * Restores original article values from the reversible backup JSON.
     * Re-enables article repair flag so it can be re-run if needed.
     */
    suspend fun restoreArticles(backupJson: String? = null): Int {
        val rawJson = backupJson ?: settingsRepository.getSettings().repairedArticlesBackupJson
        if (rawJson.isBlank()) return 0

        val records = runCatching {
            json.decodeFromString<List<RepairedArticleBackupRecord>>(rawJson)
        }.getOrNull() ?: return 0

        var restoredCount = 0
        for (record in records) {
            val word = wordDao.getById(record.id)
            if (word != null) {
                wordDao.update(word.copy(article = record.oldArticle))
                restoredCount++
            }
        }

        settingsRepository.resetArticlesRepairedFlag()
        Log.i(TAG, "Restored $restoredCount article records from backup.")
        return restoredCount
    }
}
