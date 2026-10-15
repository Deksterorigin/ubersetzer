package app.deutschlern.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dialogs")
data class DialogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startedAt: Long,
    val durationMs: Long,
    val localeTag: String = "de-DE",
    val segmentCount: Int = 0,
    val summaryJson: String? = null,
    val summaryProviderModel: String? = null,
    val summaryUpdatedAt: Long? = null,
    @ColumnInfo(defaultValue = "'COMPLETE'")
    val status: String = "COMPLETE"
)
