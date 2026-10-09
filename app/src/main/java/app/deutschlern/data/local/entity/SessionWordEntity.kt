package app.deutschlern.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "session_words",
    primaryKeys = ["sessionId", "wordId"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId", "wordId"], unique = true),
        Index(value = ["wordId"])
    ]
)
data class SessionWordEntity(
    val sessionId: Long,
    val wordId: Long,
    val translatedAt: Long
)
