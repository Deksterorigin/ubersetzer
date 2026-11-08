package app.deutschlern.data.backup

import kotlinx.serialization.Serializable

/**
 * On-disk backup format. Fields are only ever added (with defaults), never renamed or removed,
 * so that older backups stay readable by newer app versions.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 2,
    val createdAt: Long,
    val appVersion: String = "",
    val sessions: List<BackupSession> = emptyList(),
    val words: List<BackupWord> = emptyList(),
    val sessionWords: List<BackupSessionWord> = emptyList(),
    val reviews: List<BackupReview> = emptyList(),
    val apiKeys: Map<String, String> = emptyMap(),
    val dialogs: List<BackupDialog> = emptyList(),
    val textChecks: List<BackupTextCheck> = emptyList(),
    val trainerStats: BackupTrainerStats? = null
) {
    val hasUserContent: Boolean
        get() = words.isNotEmpty() || dialogs.isNotEmpty() || textChecks.isNotEmpty()

    companion object {
        const val FORMAT = "deutschlern-backup"
    }
}

@Serializable
data class BackupSession(
    val id: Long,
    val title: String,
    val date: Long,
    val isManual: Boolean
)

@Serializable
data class BackupWord(
    val id: Long,
    val lemma: String,
    val article: String? = null,
    val pos: String,
    val plural: String? = null,
    val genitive: String? = null,
    val grammarJson: String? = null,
    val meaningsJson: String,
    val synonymsJson: String = "[]",
    val collocationsJson: String = "[]",
    val selectedMeaningIndex: Int? = null,
    val timesTranslated: Int = 1,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val sessionId: Long,
    val status: String = "NEW",
    val partnerLanguage: String = "ru",
    val isDifficult: Boolean = false,
    val topicsJson: String? = null
)

@Serializable
data class BackupSessionWord(
    val sessionId: Long,
    val wordId: Long,
    val translatedAt: Long
)

@Serializable
data class BackupReview(
    val wordId: Long,
    val easeFactor: Double,
    val intervalDays: Int,
    val repetitions: Int,
    val dueAt: Long
)

@Serializable
data class BackupDialog(
    val title: String,
    val startedAt: Long,
    val durationMs: Long,
    val localeTag: String = "de-DE",
    val summaryJson: String? = null,
    val summaryProviderModel: String? = null,
    val summaryUpdatedAt: Long? = null,
    val status: String = "COMPLETE",
    val segments: List<BackupDialogSegment> = emptyList(),
    val tasks: List<BackupDialogTask> = emptyList()
)

@Serializable
data class BackupDialogSegment(
    val orderIndex: Int,
    val text: String,
    val speakerLabel: String? = null,
    val startOffsetMs: Long
)

@Serializable
data class BackupDialogTask(
    val type: String,
    val payloadJson: String,
    val resultJson: String? = null,
    val scorePercent: Int? = null,
    val createdAt: Long,
    val completedAt: Long? = null
)

@Serializable
data class BackupTextCheck(
    val originalText: String,
    val correctedText: String,
    val editsJson: String,
    val commentsJson: String,
    val finalText: String,
    val createdAt: Long,
    val providerModel: String,
    val partnerLanguage: String
)

@Serializable
data class BackupTrainerStats(
    val lastDay: Long,
    val streakDays: Int,
    val answersToday: Int
)
