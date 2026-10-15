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

@Singleton
class LegacyArticleRepairManager @Inject constructor(
    private val wordDao: WordDao,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "LegacyArticleRepair"
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
            val normalized = PosNormalizer.normalize(rawPos)

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
}
