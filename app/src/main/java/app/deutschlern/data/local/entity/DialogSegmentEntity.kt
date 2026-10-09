package app.deutschlern.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dialog_segments",
    foreignKeys = [
        ForeignKey(
            entity = DialogEntity::class,
            parentColumns = ["id"],
            childColumns = ["dialogId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["dialogId"])]
)
data class DialogSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dialogId: Long,
    val orderIndex: Int,
    val text: String,
    val speakerLabel: String? = null,
    val startOffsetMs: Long
)
