package app.deutschlern.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "text_translations")
data class TextTranslationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val source: String,
    val result: String,
    val direction: String,
    val createdAt: Long
)
