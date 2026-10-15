package app.deutschlern.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val TEST_DB = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4To5_validatesSchemaAndDataRetentionAndClassification() {
        // 1. Create database in version 4
        var db: SupportSQLiteDatabase = helper.createDatabase(TEST_DB, 4)

        // 2. Insert test rows across languages
        // Russian word
        db.execSQL(
            """
            INSERT INTO words (
                id, lemma, article, pos, plural, genitive, grammarJson, meaningsJson,
                synonymsJson, collocationsJson, selectedMeaningIndex, timesTranslated,
                firstSeenAt, lastSeenAt, sessionId, status
            ) VALUES (
                1, 'Haus', 'das', 'noun', 'Häuser', 'des Hauses', NULL,
                '[{"translation":"дом","example_de":"Das Haus","example_ru":"Дом"}]',
                '[]', '[]', 0, 1, 1000, 2000, 1, 'NEW'
            )
            """.trimIndent()
        )

        // Ukrainian word (contains 'ї')
        db.execSQL(
            """
            INSERT INTO words (
                id, lemma, article, pos, plural, genitive, grammarJson, meaningsJson,
                synonymsJson, collocationsJson, selectedMeaningIndex, timesTranslated,
                firstSeenAt, lastSeenAt, sessionId, status
            ) VALUES (
                2, 'Buch', 'das', 'noun', 'Bücher', 'des Buches', NULL,
                '[{"translation":"книга","example_de":"Das Buch","example_ru":"Ця книга"},{"translation":"поїзд","example_de":"Der Zug","example_ru":""}]',
                '[]', '[]', 0, 2, 1000, 2000, 1, 'LEARNING'
            )
            """.trimIndent()
        )

        // Persian word (contains 'پ')
        db.execSQL(
            """
            INSERT INTO words (
                id, lemma, article, pos, plural, genitive, grammarJson, meaningsJson,
                synonymsJson, collocationsJson, selectedMeaningIndex, timesTranslated,
                firstSeenAt, lastSeenAt, sessionId, status
            ) VALUES (
                3, 'Vater', 'der', 'noun', 'Väter', 'des Vaters', NULL,
                '[{"translation":"پدر","example_de":"Der Vater","example_ru":""}]',
                '[]', '[]', 0, 1, 1000, 2000, 1, 'NEW'
            )
            """.trimIndent()
        )

        // Arabic word (Arabic script without Persian-specific letters)
        db.execSQL(
            """
            INSERT INTO words (
                id, lemma, article, pos, plural, genitive, grammarJson, meaningsJson,
                synonymsJson, collocationsJson, selectedMeaningIndex, timesTranslated,
                firstSeenAt, lastSeenAt, sessionId, status
            ) VALUES (
                4, 'Stadt', 'die', 'noun', 'Städte', 'der Stadt', NULL,
                '[{"translation":"مدينة","example_de":"Die Stadt","example_ru":""}]',
                '[]', '[]', 0, 1, 1000, 2000, 1, 'NEW'
            )
            """.trimIndent()
        )

        // Latin ambiguous (English / Romanian)
        db.execSQL(
            """
            INSERT INTO words (
                id, lemma, article, pos, plural, genitive, grammarJson, meaningsJson,
                synonymsJson, collocationsJson, selectedMeaningIndex, timesTranslated,
                firstSeenAt, lastSeenAt, sessionId, status
            ) VALUES (
                5, 'Cat', 'die', 'noun', 'Katzen', 'der Katze', NULL,
                '[{"translation":"cat","example_de":"Die Katze","example_ru":""}]',
                '[]', '[]', 0, 1, 1000, 2000, 1, 'NEW'
            )
            """.trimIndent()
        )

        db.close()

        // 3. Run migration 4 to 5 and validate resulting schema against 5.json
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseMigrations.MIGRATION_4_5)

        // 4. Validate data retention and post-migration classification
        val cursor = db.query("SELECT id, lemma, pos, partnerLanguage, timesTranslated, status FROM words ORDER BY id ASC")
        cursor.use { c ->
            assertTrue(c.moveToNext())
            assertEquals(1L, c.getLong(c.getColumnIndexOrThrow("id")))
            assertEquals("Haus", c.getString(c.getColumnIndexOrThrow("lemma")))
            assertEquals("noun", c.getString(c.getColumnIndexOrThrow("pos")))
            assertEquals("ru", c.getString(c.getColumnIndexOrThrow("partnerLanguage")))
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("timesTranslated")))

            assertTrue(c.moveToNext())
            assertEquals(2L, c.getLong(c.getColumnIndexOrThrow("id")))
            assertEquals("Buch", c.getString(c.getColumnIndexOrThrow("lemma")))
            assertEquals("uk", c.getString(c.getColumnIndexOrThrow("partnerLanguage")))
            assertEquals(2, c.getInt(c.getColumnIndexOrThrow("timesTranslated")))

            assertTrue(c.moveToNext())
            assertEquals(3L, c.getLong(c.getColumnIndexOrThrow("id")))
            assertEquals("Vater", c.getString(c.getColumnIndexOrThrow("lemma")))
            assertEquals("fa", c.getString(c.getColumnIndexOrThrow("partnerLanguage")))

            assertTrue(c.moveToNext())
            assertEquals(4L, c.getLong(c.getColumnIndexOrThrow("id")))
            assertEquals("Stadt", c.getString(c.getColumnIndexOrThrow("lemma")))
            assertEquals("ar", c.getString(c.getColumnIndexOrThrow("partnerLanguage")))

            assertTrue(c.moveToNext())
            assertEquals(5L, c.getLong(c.getColumnIndexOrThrow("id")))
            assertEquals("Cat", c.getString(c.getColumnIndexOrThrow("lemma")))
            assertEquals("ru", c.getString(c.getColumnIndexOrThrow("partnerLanguage")))
        }

        // 5. Verify unique index on (lemma, pos, partnerLanguage) allows same lemma+pos with different partnerLanguage
        db.execSQL(
            """
            INSERT INTO words (
                id, lemma, article, pos, plural, genitive, grammarJson, meaningsJson,
                synonymsJson, collocationsJson, selectedMeaningIndex, timesTranslated,
                firstSeenAt, lastSeenAt, sessionId, status, partnerLanguage
            ) VALUES (
                6, 'Haus', 'das', 'noun', 'Häuser', 'des Hauses', NULL,
                '[{"translation":"house","example_de":"Das Haus","example_ru":""}]',
                '[]', '[]', 0, 1, 1000, 2000, 1, 'NEW', 'en'
            )
            """.trimIndent()
        )

        val countCursor = db.query("SELECT COUNT(*) FROM words WHERE lemma = 'Haus'")
        countCursor.use { c ->
            assertTrue(c.moveToNext())
            assertEquals(2L, c.getLong(0))
        }
    }

    @Test
    fun migrate7To8_validatesSchemaAndDefaultStatus() {
        // 1. Create database in version 7
        var db: SupportSQLiteDatabase = helper.createDatabase(TEST_DB, 7)

        // 2. Insert test dialog without status column (v7 schema)
        db.execSQL(
            """
            INSERT INTO dialogs (
                id, title, createdAt, durationMs, segmentCount, audioUri, summaryJson
            ) VALUES (
                42, 'Test Dialog', 1700000000000, 120000, 5, NULL, NULL
            )
            """.trimIndent()
        )
        db.close()

        // 3. Run migration 7 to 8 and validate schema
        db = helper.runMigrationsAndValidate(TEST_DB, 8, true, DatabaseMigrations.MIGRATION_7_8)

        // 4. Verify existing dialog got default status 'COMPLETE'
        val cursor = db.query("SELECT id, title, status FROM dialogs WHERE id = 42")
        cursor.use { c ->
            assertTrue(c.moveToNext())
            assertEquals(42L, c.getLong(c.getColumnIndexOrThrow("id")))
            assertEquals("Test Dialog", c.getString(c.getColumnIndexOrThrow("title")))
            assertEquals("COMPLETE", c.getString(c.getColumnIndexOrThrow("status")))
        }
    }

    @Test
    fun migrateAll_fromOldestExportedSchemaTo8_runsChain() {
        // 1. Create database in oldest exported schema (v4)
        var db: SupportSQLiteDatabase = helper.createDatabase(TEST_DB, 4)
        db.close()

        // 2. Run chained migrations from 4 to 8
        db = helper.runMigrationsAndValidate(
            TEST_DB,
            8,
            true,
            DatabaseMigrations.MIGRATION_4_5,
            DatabaseMigrations.MIGRATION_5_6,
            DatabaseMigrations.MIGRATION_6_7,
            DatabaseMigrations.MIGRATION_7_8
        )
        assertNotNull(db)
    }
}
