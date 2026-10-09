package app.deutschlern.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dialog_tasks",
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
data class DialogTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dialogId: Long,
    val type: String, // MCQ, GAP_FILL, OPEN_RESPONSE
    val payloadJson: String,
    val resultJson: String? = null,
    val scorePercent: Int? = null,
    val createdAt: Long,
    val completedAt: Long? = null
)
