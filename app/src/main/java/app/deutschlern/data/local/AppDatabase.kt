package app.deutschlern.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import app.deutschlern.data.local.dao.CacheDao
import app.deutschlern.data.local.dao.SessionDao
import app.deutschlern.data.local.dao.SessionWordDao
import app.deutschlern.data.local.dao.TextTranslationDao
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.data.local.entity.CacheEntryEntity
import app.deutschlern.data.local.entity.SessionEntity
import app.deutschlern.data.local.entity.SessionWordEntity
import app.deutschlern.data.local.entity.TextTranslationEntity
import app.deutschlern.data.local.entity.WordEntryEntity

import app.deutschlern.data.local.dao.ReviewDao
import app.deutschlern.data.local.dao.TextCheckDao
import app.deutschlern.data.local.entity.ReviewStateEntity
import app.deutschlern.data.local.entity.TextCheckEntity

import app.deutschlern.data.local.dao.DialogDao
import app.deutschlern.data.local.dao.DialogSegmentDao
import app.deutschlern.data.local.dao.DialogTaskDao
import app.deutschlern.data.local.entity.DialogEntity
import app.deutschlern.data.local.entity.DialogSegmentEntity
import app.deutschlern.data.local.entity.DialogTaskEntity

@Database(
    entities = [
        CacheEntryEntity::class,
        WordEntryEntity::class,
        SessionEntity::class,
        SessionWordEntity::class,
        TextTranslationEntity::class,
        ReviewStateEntity::class,
        TextCheckEntity::class,
        DialogEntity::class,
        DialogSegmentEntity::class,
        DialogTaskEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun wordDao(): WordDao
    abstract fun sessionDao(): SessionDao
    abstract fun sessionWordDao(): SessionWordDao
    abstract fun textTranslationDao(): TextTranslationDao
    abstract fun reviewDao(): ReviewDao
    abstract fun textCheckDao(): TextCheckDao
    abstract fun dialogDao(): DialogDao
    abstract fun dialogSegmentDao(): DialogSegmentDao
    abstract fun dialogTaskDao(): DialogTaskDao
}

