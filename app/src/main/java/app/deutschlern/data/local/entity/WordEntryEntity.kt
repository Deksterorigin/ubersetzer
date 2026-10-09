package app.deutschlern.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
    indices = [
        Index(value = ["lemma", "pos", "partnerLanguage"], unique = true)
    ]
)
data class WordEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
    @ColumnInfo(defaultValue = "'ru'")
    val partnerLanguage: String = "ru"
)
