package app.deutschlern.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

data class MigrationClassificationStats(
    val total: Int = 0,
    val ruCount: Int = 0,
    val ukCount: Int = 0,
    val arCount: Int = 0,
    val faCount: Int = 0,
    val latinAmbiguousCount: Int = 0
)

enum class PartnerLanguageClassification {
    UK,
    FA,
    AR,
    RU,
    LATIN_AMBIGUOUS,
    OTHER
}

object DatabaseMigrations {

    private val UKRAINIAN_REGEX = Regex("[\\u0456\\u0457\\u0454\\u0491\\u0406\\u0407\\u0404\\u0490іїєґІЇЄҐ]")
    private val PERSIAN_REGEX = Regex("[\\u067E\\u0686\\u0698\\u06AFپچژگ]")
    private val ARABIC_REGEX = Regex("[\\u0600-\\u06FF]")
    private val CYRILLIC_REGEX = Regex("[\\u0430-\\u044F\\u0410-\\u042F\\u0451\\u0401а-яА-ЯёЁ]")
    private val LATIN_REGEX = Regex("[a-zA-Z]")

    fun migrateToVersion5(db: SupportSQLiteDatabase) {
        // 1. Ensure translation_cache has semanticKey, providerId, and modelName
        val cursor = db.query("PRAGMA table_info(translation_cache)")
        var hasSemanticKey = false
        var hasProviderId = false
        var hasModelName = false
        cursor.use { c ->
            val nameCol = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (nameCol >= 0) {
                    when (c.getString(nameCol)) {
                        "semanticKey" -> hasSemanticKey = true
                        "providerId" -> hasProviderId = true
                        "modelName" -> hasModelName = true
                    }
                }
            }
        }
        if (!hasSemanticKey) {
            db.execSQL("ALTER TABLE translation_cache ADD COLUMN semanticKey TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_translation_cache_semanticKey ON translation_cache(semanticKey)")
        }
        if (!hasProviderId) {
            db.execSQL("ALTER TABLE translation_cache ADD COLUMN providerId TEXT NOT NULL DEFAULT ''")
        }
        if (!hasModelName) {
            db.execSQL("ALTER TABLE translation_cache ADD COLUMN modelName TEXT NOT NULL DEFAULT ''")
        }

        // 2. Ensure words table exists and has partnerLanguage
        val wordsCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='words'")
        val wordsExist = wordsCursor.use { it.moveToNext() }
        if (!wordsExist) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `words` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `lemma` TEXT NOT NULL,
                    `article` TEXT,
                    `pos` TEXT NOT NULL,
                    `plural` TEXT,
                    `genitive` TEXT,
                    `grammarJson` TEXT,
                    `meaningsJson` TEXT NOT NULL,
                    `synonymsJson` TEXT NOT NULL,
                    `collocationsJson` TEXT NOT NULL,
                    `selectedMeaningIndex` INTEGER,
                    `timesTranslated` INTEGER NOT NULL,
                    `firstSeenAt` INTEGER NOT NULL,
                    `lastSeenAt` INTEGER NOT NULL,
                    `sessionId` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `partnerLanguage` TEXT NOT NULL DEFAULT 'ru'
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_words_lemma_pos_partnerLanguage` ON `words` (`lemma`, `pos`, `partnerLanguage`)")
        } else {
            val wordColsCursor = db.query("PRAGMA table_info(words)")
            var hasPartnerLang = false
            wordColsCursor.use { c ->
                val nameCol = c.getColumnIndex("name")
                while (c.moveToNext()) {
                    if (nameCol >= 0 && c.getString(nameCol) == "partnerLanguage") {
                        hasPartnerLang = true
                    }
                }
            }
            if (!hasPartnerLang) {
                db.execSQL("ALTER TABLE words ADD COLUMN partnerLanguage TEXT NOT NULL DEFAULT 'ru'")
                db.execSQL("DROP INDEX IF EXISTS index_words_lemma_pos")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_words_lemma_pos_partnerLanguage ON words(lemma, pos, partnerLanguage)")
                classifyExistingWords(db)
            }
        }

        // 3. Ensure sessions table exists
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `date` INTEGER NOT NULL,
                `isManual` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 4. Ensure session_words table exists
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `session_words` (
                `sessionId` INTEGER NOT NULL,
                `wordId` INTEGER NOT NULL,
                `translatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`sessionId`, `wordId`),
                FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`wordId`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_session_words_sessionId_wordId` ON `session_words` (`sessionId`, `wordId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_words_wordId` ON `session_words` (`wordId`)")

        // 5. Ensure text_translations table exists
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `text_translations` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `source` TEXT NOT NULL,
                `result` TEXT NOT NULL,
                `direction` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 6. Ensure review_states table exists
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `review_states` (
                `wordId` INTEGER NOT NULL,
                `easeFactor` REAL NOT NULL,
                `intervalDays` INTEGER NOT NULL,
                `repetitions` INTEGER NOT NULL,
                `dueAt` INTEGER NOT NULL,
                PRIMARY KEY(`wordId`),
                FOREIGN KEY(`wordId`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_review_states_wordId` ON `review_states` (`wordId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_states_dueAt` ON `review_states` (`dueAt`)")
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE words ADD COLUMN partnerLanguage TEXT NOT NULL DEFAULT 'ru'")
            db.execSQL("DROP INDEX IF EXISTS index_words_lemma_pos")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_words_lemma_pos_partnerLanguage ON words(lemma, pos, partnerLanguage)")
            classifyExistingWords(db)
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `text_checks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `originalText` TEXT NOT NULL,
                    `correctedText` TEXT NOT NULL,
                    `editsJson` TEXT NOT NULL,
                    `commentsJson` TEXT NOT NULL,
                    `finalText` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `providerModel` TEXT NOT NULL,
                    `partnerLanguage` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_checks_createdAt` ON `text_checks` (`createdAt`)")
        }
    }

    val MIGRATION_1_6: Migration = object : Migration(1, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateToVersion5(db)
            MIGRATION_5_6.migrate(db)
        }
    }

    val MIGRATION_2_6: Migration = object : Migration(2, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateToVersion5(db)
            MIGRATION_5_6.migrate(db)
        }
    }

    val MIGRATION_3_6: Migration = object : Migration(3, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateToVersion5(db)
            MIGRATION_5_6.migrate(db)
        }
    }

    val MIGRATION_4_6: Migration = object : Migration(4, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_4_5.migrate(db)
            MIGRATION_5_6.migrate(db)
        }
    }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `dialogs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `startedAt` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `localeTag` TEXT NOT NULL,
                    `segmentCount` INTEGER NOT NULL,
                    `summaryJson` TEXT,
                    `summaryProviderModel` TEXT,
                    `summaryUpdatedAt` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `dialog_segments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dialogId` INTEGER NOT NULL,
                    `orderIndex` INTEGER NOT NULL,
                    `text` TEXT NOT NULL,
                    `speakerLabel` TEXT,
                    `startOffsetMs` INTEGER NOT NULL,
                    FOREIGN KEY(`dialogId`) REFERENCES `dialogs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dialog_segments_dialogId` ON `dialog_segments` (`dialogId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `dialog_tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dialogId` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `resultJson` TEXT,
                    `scorePercent` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    FOREIGN KEY(`dialogId`) REFERENCES `dialogs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dialog_tasks_dialogId` ON `dialog_tasks` (`dialogId`)")
        }
    }

    val MIGRATION_5_7: Migration = object : Migration(5, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_5_6.migrate(db)
            MIGRATION_6_7.migrate(db)
        }
    }

    val MIGRATION_4_7: Migration = object : Migration(4, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_4_5.migrate(db)
            MIGRATION_5_6.migrate(db)
            MIGRATION_6_7.migrate(db)
        }
    }

    val MIGRATION_3_7: Migration = object : Migration(3, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateToVersion5(db)
            MIGRATION_5_6.migrate(db)
            MIGRATION_6_7.migrate(db)
        }
    }

    val MIGRATION_2_7: Migration = object : Migration(2, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateToVersion5(db)
            MIGRATION_5_6.migrate(db)
            MIGRATION_6_7.migrate(db)
        }
    }

    val MIGRATION_1_7: Migration = object : Migration(1, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateToVersion5(db)
            MIGRATION_5_6.migrate(db)
            MIGRATION_6_7.migrate(db)
        }
    }

    val MIGRATION_1_5: Migration = object : Migration(1, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateToVersion5(db)
        }
    }

    val MIGRATION_2_5: Migration = object : Migration(2, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateToVersion5(db)
        }
    }

    val MIGRATION_3_5: Migration = object : Migration(3, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateToVersion5(db)
        }
    }

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateToVersion5(db)
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateToVersion5(db)
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateToVersion5(db)
        }
    }

    fun classifyLanguage(text: String): PartnerLanguageClassification {
        return when {
            UKRAINIAN_REGEX.containsMatchIn(text) -> PartnerLanguageClassification.UK
            PERSIAN_REGEX.containsMatchIn(text) -> PartnerLanguageClassification.FA
            ARABIC_REGEX.containsMatchIn(text) -> PartnerLanguageClassification.AR
            CYRILLIC_REGEX.containsMatchIn(text) -> PartnerLanguageClassification.RU
            LATIN_REGEX.containsMatchIn(text) -> PartnerLanguageClassification.LATIN_AMBIGUOUS
            else -> PartnerLanguageClassification.OTHER
        }
    }

    fun classifyExistingWords(db: SupportSQLiteDatabase): MigrationClassificationStats {
        var total = 0
        var ruCount = 0
        var ukCount = 0
        var arCount = 0
        var faCount = 0
        var latinAmbiguousCount = 0

        val cursor = db.query("SELECT id, meaningsJson FROM words")
        cursor.use { c ->
            val idCol = c.getColumnIndex("id")
            val meaningsCol = c.getColumnIndex("meaningsJson")

            while (c.moveToNext()) {
                total++
                val id = if (idCol >= 0) c.getLong(idCol) else 0L
                val meaningsJson = if (meaningsCol >= 0) c.getString(meaningsCol).orEmpty() else ""

                when (classifyLanguage(meaningsJson)) {
                    PartnerLanguageClassification.UK -> {
                        ukCount++
                        db.execSQL("UPDATE words SET partnerLanguage = 'uk' WHERE id = $id")
                    }
                    PartnerLanguageClassification.FA -> {
                        faCount++
                        db.execSQL("UPDATE words SET partnerLanguage = 'fa' WHERE id = $id")
                    }
                    PartnerLanguageClassification.AR -> {
                        arCount++
                        db.execSQL("UPDATE words SET partnerLanguage = 'ar' WHERE id = $id")
                    }
                    PartnerLanguageClassification.RU -> {
                        ruCount++
                    }
                    PartnerLanguageClassification.LATIN_AMBIGUOUS -> {
                        latinAmbiguousCount++
                    }
                    PartnerLanguageClassification.OTHER -> {
                        ruCount++
                    }
                }
            }
        }

        return MigrationClassificationStats(
            total = total,
            ruCount = ruCount,
            ukCount = ukCount,
            arCount = arCount,
            faCount = faCount,
            latinAmbiguousCount = latinAmbiguousCount
        )
    }
}
