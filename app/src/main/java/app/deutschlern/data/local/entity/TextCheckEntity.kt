package app.deutschlern.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "text_checks",
    indices = [
        Index(value = ["createdAt"])
    ]
)
data class TextCheckEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalText: String,
    val correctedText: String,
    val editsJson: String,
    val commentsJson: String,
    val finalText: String,
    val createdAt: Long,
    val providerModel: String,
    val partnerLanguage: String
)
