package app.deutschlern.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "translation_cache",
    indices = [Index(value = ["semanticKey"])]
)
data class CacheEntryEntity(
    @PrimaryKey
    val cacheKey: String,
    @ColumnInfo(name = "semanticKey", defaultValue = "")
    val semanticKey: String = "",
    val responseJson: String,
    val providerId: String = "",
    val modelName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
