package app.deutschlern.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.deutschlern.data.local.entity.CacheEntryEntity

@Dao
interface CacheDao {

    @Query("SELECT * FROM translation_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): CacheEntryEntity?

    @Query("SELECT * FROM translation_cache WHERE semanticKey = :semanticKey ORDER BY createdAt DESC LIMIT 1")
    suspend fun getBySemanticKey(semanticKey: String): CacheEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CacheEntryEntity): Long

    @Query("DELETE FROM translation_cache WHERE createdAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long): Int

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun count(): Int

    @Query("DELETE FROM translation_cache")
    suspend fun clearAll(): Int
}
