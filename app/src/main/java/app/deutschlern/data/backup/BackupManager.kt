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
import app.deutschlern.data.local.dao.DialogDao
import app.deutschlern.data.local.dao.DialogSegmentDao
import app.deutschlern.data.local.dao.DialogTaskDao
import app.deutschlern.data.local.dao.ReviewDao
import app.deutschlern.data.local.dao.SessionDao
import app.deutschlern.data.local.dao.SessionWordDao
import app.deutschlern.data.local.dao.TextCheckDao
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.data.local.entity.DialogEntity
import app.deutschlern.data.local.entity.DialogSegmentEntity
import app.deutschlern.data.local.entity.DialogTaskEntity
import app.deutschlern.data.local.entity.ReviewStateEntity
import app.deutschlern.data.local.entity.SessionEntity
import app.deutschlern.data.local.entity.SessionWordEntity
import app.deutschlern.data.local.entity.TextCheckEntity
import app.deutschlern.data.local.entity.WordEntryEntity
import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.data.trainer.TrainerStatsStore
import app.deutschlern.di.IoDispatcher
import app.deutschlern.domain.sanitizer.PosNormalizer
import app.deutschlern.util.runSuspendCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_prefs")

data class BackupSettings(
    val autoBackupEnabled: Boolean = true,
    val includeApiKeys: Boolean = false,
    val lastAutoBackupAt: Long? = null
)

data class RestoreResult(
    val wordsAdded: Int,
    val wordsAlreadyPresent: Int,
    val keysRestored: Int,
    val dialogsAdded: Int = 0,
    val checksAdded: Int = 0
)

/**
 * Saves the user's data (words, SM-2 progress, lessons, dialogues, text checks, streak) to a JSON file.
 * Copies saved by hand can optionally include the API keys.
 *
 * App updates never touch app data; what loses it is uninstalling, "Clear data" or moving to a new
 * phone. For that case daily automatic copies are kept in Documents/DeutschLern, which survive
 * uninstallation, and the user can save or restore a copy anywhere via the system file picker.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wordDao: WordDao,
    private val sessionDao: SessionDao,
    private val sessionWordDao: SessionWordDao,
    private val reviewDao: ReviewDao,
    private val dialogDao: DialogDao,
    private val dialogSegmentDao: DialogSegmentDao,
    private val dialogTaskDao: DialogTaskDao,
    private val textCheckDao: TextCheckDao,
    private val trainerStatsStore: TrainerStatsStore,
    private val secureKeyStorage: SecureKeyStorage,
    private val transactionRunner: TransactionRunner,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    val settings: Flow<BackupSettings>
        get() = context.backupPrefsDataStore.data.map { prefs ->
            BackupSettings(
                autoBackupEnabled = prefs[KEY_AUTO_ENABLED] ?: true,
                includeApiKeys = prefs[KEY_INCLUDE_KEYS] ?: false,
                lastAutoBackupAt = prefs[KEY_LAST_AUTO_BACKUP]
            )
        }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.backupPrefsDataStore.edit { it[KEY_AUTO_ENABLED] = enabled }
        if (enabled) writeAutoBackup()
    }

    /** Applies to copies saved by hand. The automatic copy in shared storage never contains keys. */
    suspend fun setIncludeApiKeys(include: Boolean) {
        context.backupPrefsDataStore.edit { it[KEY_INCLUDE_KEYS] = include }
    }

    private suspend fun buildBackup(includeKeys: Boolean): BackupFile {
        val backup = transactionRunner.run {
            val segmentsByDialog = mutableMapOf<Long, List<BackupDialogSegment>>()
            val tasksByDialog = mutableMapOf<Long, List<BackupDialogTask>>()
            val dialogs = dialogDao.getAllDialogsSync()
            dialogs.forEach { dialog ->
                segmentsByDialog[dialog.id] = dialogSegmentDao.getSegmentsSync(dialog.id).map {
                    BackupDialogSegment(it.orderIndex, it.text, it.speakerLabel, it.startOffsetMs)
                }
                tasksByDialog[dialog.id] = dialogTaskDao.getTasksForDialogSync(dialog.id).map {
                    BackupDialogTask(it.type, it.payloadJson, it.resultJson, it.scorePercent, it.createdAt, it.completedAt)
                }
            }
            BackupFile(
                createdAt = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                sessions = sessionDao.getAllSessionsSync().map { BackupSession(it.id, it.title, it.date, it.isManual) },
                words = wordDao.getAllWordsSync().map { it.toBackup() },
                sessionWords = sessionWordDao.getAllSync().map { BackupSessionWord(it.sessionId, it.wordId, it.translatedAt) },
                reviews = reviewDao.getAllReviewStatesSync().map {
                    BackupReview(it.wordId, it.easeFactor, it.intervalDays, it.repetitions, it.dueAt)
                },
                dialogs = dialogs.map { dialog ->
                    BackupDialog(
                        title = dialog.title,
                        startedAt = dialog.startedAt,
                        durationMs = dialog.durationMs,
                        localeTag = dialog.localeTag,
                        summaryJson = dialog.summaryJson,
                        summaryProviderModel = dialog.summaryProviderModel,
                        summaryUpdatedAt = dialog.summaryUpdatedAt,
                        status = dialog.status,
                        segments = segmentsByDialog[dialog.id].orEmpty(),
                        tasks = tasksByDialog[dialog.id].orEmpty()
                    )
                },
                textChecks = textCheckDao.getAllSync().map {
                    BackupTextCheck(
                        originalText = it.originalText,
                        correctedText = it.correctedText,
                        editsJson = it.editsJson,
                        commentsJson = it.commentsJson,
                        finalText = it.finalText,
                        createdAt = it.createdAt,
                        providerModel = it.providerModel,
                        partnerLanguage = it.partnerLanguage
                    )
                }
            )
        }
        return backup.copy(
            trainerStats = trainerStatsStore.exportStats(),
            apiKeys = if (includeKeys) secureKeyStorage.exportKeys() else emptyMap()
        )
    }

    suspend fun createBackupJson(): String = withContext(ioDispatcher) {
        json.encodeToString(BackupFile.serializer(), buildBackup(settings.first().includeApiKeys))
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

            var dialogsAdded = 0
            backup.dialogs.forEach { dialog ->
                if (dialogDao.getByStartedAtAndTitle(dialog.startedAt, dialog.title) != null) return@forEach
                val dialogId = dialogDao.insertDialog(
                    DialogEntity(
                        title = dialog.title,
                        startedAt = dialog.startedAt,
                        durationMs = dialog.durationMs,
                        localeTag = dialog.localeTag,
                        segmentCount = dialog.segments.size,
                        summaryJson = dialog.summaryJson,
                        summaryProviderModel = dialog.summaryProviderModel,
                        summaryUpdatedAt = dialog.summaryUpdatedAt,
                        // No recording can be running in a restored dialog
                        status = if (dialog.status == STATUS_RECORDING) STATUS_INTERRUPTED else dialog.status
                    )
                )
                dialogSegmentDao.insertSegments(
                    dialog.segments.map {
                        DialogSegmentEntity(
                            dialogId = dialogId,
                            orderIndex = it.orderIndex,
                            text = it.text,
                            speakerLabel = it.speakerLabel,
                            startOffsetMs = it.startOffsetMs
                        )
                    }
                )
                dialogTaskDao.insertTasks(
                    dialog.tasks.map {
                        DialogTaskEntity(
                            dialogId = dialogId,
                            type = it.type,
                            payloadJson = it.payloadJson,
                            resultJson = it.resultJson,
                            scorePercent = it.scorePercent,
                            createdAt = it.createdAt,
                            completedAt = it.completedAt
                        )
                    }
                )
                dialogsAdded++
            }

            var checksAdded = 0
            backup.textChecks.forEach { check ->
                if (textCheckDao.exists(check.createdAt, check.originalText)) return@forEach
                textCheckDao.insert(
                    TextCheckEntity(
                        originalText = check.originalText,
                        correctedText = check.correctedText,
                        editsJson = check.editsJson,
                        commentsJson = check.commentsJson,
                        finalText = check.finalText,
                        createdAt = check.createdAt,
                        providerModel = check.providerModel,
                        partnerLanguage = check.partnerLanguage
                    )
                )
                checksAdded++
            }

            RestoreResult(
                wordsAdded = added,
                wordsAlreadyPresent = present,
                keysRestored = 0,
                dialogsAdded = dialogsAdded,
                checksAdded = checksAdded
            )
        }

        backup.trainerStats?.let { trainerStatsStore.restoreStats(it) }

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
        merge(
            wordDao.getAllWords(),
            reviewDao.getAllReviewStates(),
            sessionWordDao.getAll(),
            dialogDao.getAllDialogs(),
            dialogTaskDao.getAllTasks(),
            textCheckDao.getAll()
        )
            .debounce(AUTO_BACKUP_DEBOUNCE_MS)
            .collect {
                if (settings.first().autoBackupEnabled) {
                    runSuspendCatching { writeAutoBackup() }
                }
            }
    }

    private suspend fun writeAutoBackup() = withContext(ioDispatcher) {
        if (!isAutoBackupSupported) return@withContext
        val backup = buildBackup(includeKeys = false)
        // An empty database right after a reinstall must never overwrite a good copy
        if (!backup.hasUserContent) return@withContext
        writeToDocuments(json.encodeToString(BackupFile.serializer(), backup).toByteArray(Charsets.UTF_8))
        context.backupPrefsDataStore.edit { it[KEY_LAST_AUTO_BACKUP] = System.currentTimeMillis() }
    }

    /**
     * One file per day. The new copy is fully written as a pending entry first, so a crash or a full
     * disk leaves today's previous copy and all earlier days untouched.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToDocuments(bytes: ByteArray) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val pending = resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "deutschlern-backup-writing.json")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, AUTO_BACKUP_DIR)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        ) ?: error("Cannot create backup file")

        try {
            val stream = resolver.openOutputStream(pending, "w") ?: error("Cannot open backup file")
            stream.use { it.write(bytes) }
        } catch (e: Exception) {
            resolver.delete(pending, null, null)
            throw e
        }

        val todayName = dailyBackupName(LocalDate.now())
        val existing = queryOwnBackups(collection)
        existing[todayName]?.let { resolver.delete(it, null, null) }
        resolver.update(
            pending,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, todayName)
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            },
            null,
            null
        )

        backupsToDelete(existing.keys + todayName).forEach { name ->
            existing[name]?.let { resolver.delete(it, null, null) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryOwnBackups(collection: Uri): Map<String, Uri> {
        val found = mutableMapOf<String, Uri>()
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(AUTO_BACKUP_DIR),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                found[cursor.getString(1)] = ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
        }
        return found
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
        private const val LEGACY_BACKUP_FILE = "deutschlern-backup.json"
        private const val DAILY_BACKUPS_TO_KEEP = 7
        private const val AUTO_BACKUP_DEBOUNCE_MS = 10_000L
        private const val STATUS_RECORDING = "RECORDING"
        private const val STATUS_INTERRUPTED = "INTERRUPTED"
        private val DAILY_BACKUP_NAME = Regex("""deutschlern-backup-\d{4}-\d{2}-\d{2}\.json""")

        val isAutoBackupSupported: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        internal fun dailyBackupName(date: LocalDate): String = "deutschlern-backup-$date.json"

        /**
         * Daily copies beyond the newest ones, plus the pre-1.4 single file, which may still hold
         * API keys in plain text. Files with other names belong to the user and are left alone.
         */
        internal fun backupsToDelete(names: Collection<String>, keep: Int = DAILY_BACKUPS_TO_KEEP): List<String> {
            val outdated = names.filter { DAILY_BACKUP_NAME.matches(it) }.sortedDescending().drop(keep)
            return if (LEGACY_BACKUP_FILE in names) outdated + LEGACY_BACKUP_FILE else outdated
        }

        private val KEY_AUTO_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_INCLUDE_KEYS = booleanPreferencesKey("backup_include_api_keys")
        private val KEY_LAST_AUTO_BACKUP = longPreferencesKey("last_auto_backup_at")
    }
}
