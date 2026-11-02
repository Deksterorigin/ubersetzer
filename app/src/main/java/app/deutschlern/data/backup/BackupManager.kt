package app.deutschlern.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.deutschlern.BuildConfig
import app.deutschlern.data.local.TransactionRunner
import app.deutschlern.data.local.dao.ReviewDao
import app.deutschlern.data.local.dao.SessionDao
import app.deutschlern.data.local.dao.SessionWordDao
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.data.local.entity.ReviewStateEntity
import app.deutschlern.data.local.entity.SessionEntity
import app.deutschlern.data.local.entity.SessionWordEntity
import app.deutschlern.data.local.entity.WordEntryEntity
import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.di.IoDispatcher
import app.deutschlern.domain.sanitizer.PosNormalizer
import app.deutschlern.util.runSuspendCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_prefs")

data class BackupSettings(
    val autoBackupEnabled: Boolean = true,
    val includeApiKeys: Boolean = true,
    val lastAutoBackupAt: Long? = null
)

data class RestoreResult(
    val wordsAdded: Int,
    val wordsAlreadyPresent: Int,
    val keysRestored: Int
)

/**
 * Saves the vocabulary (words, SM-2 progress, lessons) and optionally the API keys to a JSON file.
 *
 * App updates never touch app data; what loses it is uninstalling, "Clear data" or moving to a new
 * phone. For that case an automatic copy is kept in Documents/DeutschLern, which survives
 * uninstallation, and the user can save or restore a copy anywhere via the system file picker.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wordDao: WordDao,
    private val sessionDao: SessionDao,
    private val sessionWordDao: SessionWordDao,
    private val reviewDao: ReviewDao,
    private val secureKeyStorage: SecureKeyStorage,
    private val transactionRunner: TransactionRunner,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    val settings: Flow<BackupSettings>
        get() = context.backupPrefsDataStore.data.map { prefs ->
            BackupSettings(
                autoBackupEnabled = prefs[KEY_AUTO_ENABLED] ?: true,
                includeApiKeys = prefs[KEY_INCLUDE_KEYS] ?: true,
                lastAutoBackupAt = prefs[KEY_LAST_AUTO_BACKUP]
            )
        }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.backupPrefsDataStore.edit { it[KEY_AUTO_ENABLED] = enabled }
        if (enabled) writeAutoBackup()
    }

    suspend fun setIncludeApiKeys(include: Boolean) {
        context.backupPrefsDataStore.edit { it[KEY_INCLUDE_KEYS] = include }
        // Rewrite immediately so turning keys off also removes them from the existing file
        if (settings.first().autoBackupEnabled) writeAutoBackup()
    }

    suspend fun createBackupJson(): String = withContext(ioDispatcher) {
        val includeKeys = settings.first().includeApiKeys
        val backup = transactionRunner.run {
            BackupFile(
                createdAt = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                sessions = sessionDao.getAllSessionsSync().map { BackupSession(it.id, it.title, it.date, it.isManual) },
                words = wordDao.getAllWordsSync().map { it.toBackup() },
                sessionWords = sessionWordDao.getAllSync().map { BackupSessionWord(it.sessionId, it.wordId, it.translatedAt) },
                reviews = reviewDao.getAllReviewStatesSync().map {
                    BackupReview(it.wordId, it.easeFactor, it.intervalDays, it.repetitions, it.dueAt)
                }
            )
        }
        val withKeys = if (includeKeys) backup.copy(apiKeys = secureKeyStorage.exportKeys()) else backup
        json.encodeToString(BackupFile.serializer(), withKeys)
    }

    suspend fun exportTo(uri: Uri): Result<Unit> = withContext(ioDispatcher) {
        runSuspendCatching {
            val content = createBackupJson()
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open $uri")
            stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        }
    }

    suspend fun importFrom(uri: Uri): Result<RestoreResult> = withContext(ioDispatcher) {
        runSuspendCatching {
            val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
            restore(stream.use { it.readBytes().toString(Charsets.UTF_8) })
        }
    }

    /**
     * Merges a backup into the current database. Words already present (same lemma, part of speech
     * and partner language) are kept as they are, so restoring never overwrites newer progress.
     */
    suspend fun restore(content: String): RestoreResult = withContext(ioDispatcher) {
        val backup = json.decodeFromString(BackupFile.serializer(), content)
        require(backup.format == BackupFile.FORMAT) { "Not a DeutschLern backup" }

        val result = transactionRunner.run {
            val sessionIds = backup.sessions.associate { session ->
                val existing = sessionDao.getByDateAndTitle(session.date, session.title)
                session.id to (existing?.id ?: sessionDao.insert(
                    SessionEntity(title = session.title, date = session.date, isManual = session.isManual)
                ))
            }

            val reviewsByWord = backup.reviews.associateBy { it.wordId }
            val wordIds = mutableMapOf<Long, Long>()
            var added = 0
            var present = 0

            backup.words.forEach { word ->
                val existing = wordDao.getByLemmaEquivalentPosAndPartner(
                    lemma = word.lemma,
                    posList = PosNormalizer.getEquivalentPosList(word.pos),
                    canonicalPos = PosNormalizer.normalize(word.pos),
                    partnerLanguage = word.partnerLanguage
                )
                if (existing != null) {
                    wordIds[word.id] = existing.id
                    present++
                    if (word.isDifficult && !existing.isDifficult) wordDao.updateDifficult(existing.id, true)
                    return@forEach
                }

                val sessionId = sessionIds[word.sessionId] ?: sessionIds.values.firstOrNull() ?: word.sessionId
                val newId = wordDao.insert(word.toEntity(sessionId))
                wordIds[word.id] = newId
                added++

                reviewsByWord[word.id]?.let { review ->
                    reviewDao.insertOrReplace(
                        ReviewStateEntity(
                            wordId = newId,
                            easeFactor = review.easeFactor,
                            intervalDays = review.intervalDays,
                            repetitions = review.repetitions,
                            dueAt = review.dueAt
                        )
                    )
                }
            }

            backup.sessionWords.forEach { link ->
                val sessionId = sessionIds[link.sessionId] ?: return@forEach
                val wordId = wordIds[link.wordId] ?: return@forEach
                if (sessionWordDao.get(sessionId, wordId) == null) {
                    sessionWordDao.insert(SessionWordEntity(sessionId, wordId, link.translatedAt))
                }
            }

            RestoreResult(wordsAdded = added, wordsAlreadyPresent = present, keysRestored = 0)
        }

        // Keys only fill gaps: a key the user entered after reinstalling is never replaced
        var keysRestored = 0
        backup.apiKeys.forEach { (providerId, key) ->
            if (key.isNotBlank() && secureKeyStorage.getKey(providerId) == null) {
                secureKeyStorage.saveKey(providerId, key)
                keysRestored++
            }
        }
        result.copy(keysRestored = keysRestored)
    }

    /** Keeps the automatic copy in Documents up to date while the app runs. */
    @OptIn(FlowPreview::class)
    suspend fun runAutoBackup() {
        combine(wordDao.getAllWords(), reviewDao.getAllReviewStates(), sessionWordDao.getAll()) { words, _, _ ->
            words.size
        }
            .debounce(AUTO_BACKUP_DEBOUNCE_MS)
            .collect { wordCount ->
                // An empty database right after a reinstall must never overwrite a good copy
                if (wordCount > 0 && settings.first().autoBackupEnabled) {
                    runSuspendCatching { writeAutoBackup() }
                }
            }
    }

    private suspend fun writeAutoBackup() = withContext(ioDispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext
        if (wordDao.getAllWordsSync().isEmpty()) return@withContext
        writeToDocuments(createBackupJson().toByteArray(Charsets.UTF_8))
        context.backupPrefsDataStore.edit { it[KEY_LAST_AUTO_BACKUP] = System.currentTimeMillis() }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToDocuments(bytes: ByteArray) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(AUTO_BACKUP_DIR, AUTO_BACKUP_FILE),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
        }

        val uri = existing ?: resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, AUTO_BACKUP_FILE)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, AUTO_BACKUP_DIR)
            }
        ) ?: error("Cannot create backup file")

        val stream = resolver.openOutputStream(uri, "wt") ?: error("Cannot open backup file")
        stream.use { it.write(bytes) }
    }

    private fun WordEntryEntity.toBackup() = BackupWord(
        id = id,
        lemma = lemma,
        article = article,
        pos = pos,
        plural = plural,
        genitive = genitive,
        grammarJson = grammarJson,
        meaningsJson = meaningsJson,
        synonymsJson = synonymsJson,
        collocationsJson = collocationsJson,
        selectedMeaningIndex = selectedMeaningIndex,
        timesTranslated = timesTranslated,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        sessionId = sessionId,
        status = status,
        partnerLanguage = partnerLanguage,
        isDifficult = isDifficult,
        topicsJson = topicsJson
    )

    private fun BackupWord.toEntity(sessionId: Long) = WordEntryEntity(
        lemma = lemma,
        article = article,
        pos = pos,
        plural = plural,
        genitive = genitive,
        grammarJson = grammarJson,
        meaningsJson = meaningsJson,
        synonymsJson = synonymsJson,
        collocationsJson = collocationsJson,
        selectedMeaningIndex = selectedMeaningIndex,
        timesTranslated = timesTranslated,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        sessionId = sessionId,
        status = status,
        partnerLanguage = partnerLanguage,
        isDifficult = isDifficult,
        topicsJson = topicsJson
    )

    companion object {
        const val AUTO_BACKUP_DIR = "Documents/DeutschLern/"
        const val AUTO_BACKUP_FILE = "deutschlern-backup.json"
        private const val AUTO_BACKUP_DEBOUNCE_MS = 10_000L

        private val KEY_AUTO_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_INCLUDE_KEYS = booleanPreferencesKey("backup_include_api_keys")
        private val KEY_LAST_AUTO_BACKUP = longPreferencesKey("last_auto_backup_at")
    }
}
