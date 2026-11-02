package app.deutschlern.data.backup

import kotlinx.serialization.Serializable

/**
 * On-disk backup format. Fields are only ever added (with defaults), never renamed or removed,
 * so that older backups stay readable by newer app versions.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val createdAt: Long,
    val appVersion: String = "",
    val sessions: List<BackupSession> = emptyList(),
    val words: List<BackupWord> = emptyList(),
    val sessionWords: List<BackupSessionWord> = emptyList(),
    val reviews: List<BackupReview> = emptyList(),
    val apiKeys: Map<String, String> = emptyMap()
) {
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
