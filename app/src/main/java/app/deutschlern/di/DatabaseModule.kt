package app.deutschlern.di

import android.content.Context
import androidx.room.Room
import app.deutschlern.data.local.AppDatabase
import app.deutschlern.data.local.DatabaseMigrations
import app.deutschlern.data.local.dao.CacheDao
import app.deutschlern.data.local.dao.DialogDao
import app.deutschlern.data.local.dao.DialogSegmentDao
import app.deutschlern.data.local.dao.DialogTaskDao
import app.deutschlern.data.local.dao.ReviewDao
import app.deutschlern.data.local.dao.SessionDao
import app.deutschlern.data.local.dao.SessionWordDao
import app.deutschlern.data.local.dao.TextCheckDao
import app.deutschlern.data.local.dao.TextTranslationDao
import app.deutschlern.data.local.dao.WordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "deutschlern.db"
        )
        .addMigrations(
            DatabaseMigrations.MIGRATION_8_9,
            DatabaseMigrations.MIGRATION_7_8,
            DatabaseMigrations.MIGRATION_6_7,
            DatabaseMigrations.MIGRATION_5_7,
            DatabaseMigrations.MIGRATION_4_7,
            DatabaseMigrations.MIGRATION_3_7,
            DatabaseMigrations.MIGRATION_2_7,
            DatabaseMigrations.MIGRATION_1_7,
            DatabaseMigrations.MIGRATION_5_6,
            DatabaseMigrations.MIGRATION_4_6,
            DatabaseMigrations.MIGRATION_3_6,
            DatabaseMigrations.MIGRATION_2_6,
            DatabaseMigrations.MIGRATION_1_6,
            DatabaseMigrations.MIGRATION_4_5,
            DatabaseMigrations.MIGRATION_3_5,
            DatabaseMigrations.MIGRATION_2_5,
            DatabaseMigrations.MIGRATION_1_5,
            DatabaseMigrations.MIGRATION_1_2,
            DatabaseMigrations.MIGRATION_2_3,
            DatabaseMigrations.MIGRATION_3_4
        )
        .build()
    }

    @Provides
    fun provideCacheDao(database: AppDatabase): CacheDao {
        return database.cacheDao()
    }

    @Provides
    fun provideWordDao(database: AppDatabase): WordDao {
        return database.wordDao()
    }

    @Provides
    fun provideSessionDao(database: AppDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideSessionWordDao(database: AppDatabase): SessionWordDao {
        return database.sessionWordDao()
    }

    @Provides
    fun provideTextTranslationDao(database: AppDatabase): TextTranslationDao {
        return database.textTranslationDao()
    }

    @Provides
    fun provideReviewDao(database: AppDatabase): ReviewDao {
        return database.reviewDao()
    }

    @Provides
    fun provideTextCheckDao(database: AppDatabase): TextCheckDao {
        return database.textCheckDao()
    }

    @Provides
    fun provideDialogDao(database: AppDatabase): DialogDao {
        return database.dialogDao()
    }

    @Provides
    fun provideDialogSegmentDao(database: AppDatabase): DialogSegmentDao {
        return database.dialogSegmentDao()
    }

    @Provides
    fun provideDialogTaskDao(database: AppDatabase): DialogTaskDao {
        return database.dialogTaskDao()
    }
}

