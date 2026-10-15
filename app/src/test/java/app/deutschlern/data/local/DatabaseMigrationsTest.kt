package app.deutschlern.data.local

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DatabaseMigrationsTest {

    @Test
    fun classifyLanguage_ukrainianSpecificLetters_returnsUk() {
        // Ukrainian specific letters: і, ї, є, ґ (lowercase and uppercase)
        val ukrainianSamples = listOf(
            "[{\"translation\":\"книга\",\"example_de\":\"\",\"example_ru\":\"Ця книга є цікавою\"}]", // contains 'є'
            "[{\"translation\":\"поїзд\",\"example_de\":\"\",\"example_ru\":\"\"}]", // contains 'ї'
            "[{\"translation\":\"місто\",\"example_de\":\"\",\"example_ru\":\"\"}]", // contains 'і'
            "[{\"translation\":\"ґанок\",\"example_de\":\"\",\"example_ru\":\"\"}]", // contains 'ґ'
            "[{\"translation\":\"ЇЖАК\",\"example_de\":\"\",\"example_ru\":\"\"}]", // uppercase 'Ї'
            "[{\"translation\":\"ІСТОРІЯ\",\"example_de\":\"\",\"example_ru\":\"\"}]", // uppercase 'І'
            "[{\"translation\":\"ЄВРОПА\",\"example_de\":\"\",\"example_ru\":\"\"}]", // uppercase 'Є'
            "[{\"translation\":\"ҐРУНТ\",\"example_de\":\"\",\"example_ru\":\"\"}]" // uppercase 'Ґ'
        )

        for (sample in ukrainianSamples) {
            assertEquals(
                "Expected UK for sample: $sample",
                PartnerLanguageClassification.UK,
                DatabaseMigrations.classifyLanguage(sample)
            )
        }
    }

    @Test
    fun classifyLanguage_persianSpecificLetters_returnsFa() {
        // Persian specific letters: پ, چ, ژ, گ
        val persianSamples = listOf(
            "[{\"translation\":\"پدر\",\"example_de\":\"\",\"example_ru\":\"\"}]", // contains 'پ'
            "[{\"translation\":\"چشم\",\"example_de\":\"\",\"example_ru\":\"\"}]", // contains 'چ'
            "[{\"translation\":\"ژاله\",\"example_de\":\"\",\"example_ru\":\"\"}]", // contains 'ژ'
            "[{\"translation\":\"گل\",\"example_de\":\"\",\"example_ru\":\"\"}]" // contains 'گ'
        )

        for (sample in persianSamples) {
            assertEquals(
                "Expected FA for sample: $sample",
                PartnerLanguageClassification.FA,
                DatabaseMigrations.classifyLanguage(sample)
            )
        }
    }

    @Test
    fun classifyLanguage_arabicScriptWithoutPersianLetters_returnsAr() {
        val arabicSamples = listOf(
            "[{\"translation\":\"كتاب\",\"example_de\":\"\",\"example_ru\":\"\"}]",
            "[{\"translation\":\"مدينة\",\"example_de\":\"\",\"example_ru\":\"\"}]",
            "[{\"translation\":\"سلام\",\"example_de\":\"\",\"example_ru\":\"\"}]"
        )

        for (sample in arabicSamples) {
            assertEquals(
                "Expected AR for sample: $sample",
                PartnerLanguageClassification.AR,
                DatabaseMigrations.classifyLanguage(sample)
            )
        }
    }

    @Test
    fun classifyLanguage_standardCyrillicWithoutUkrainianLetters_returnsRu() {
        val russianSamples = listOf(
            "[{\"translation\":\"дом\",\"example_de\":\"\",\"example_ru\":\"красивый дом\"}]",
            "[{\"translation\":\"яблоко\",\"example_de\":\"\",\"example_ru\":\"\"}]",
            "[{\"translation\":\"хорошо\",\"example_de\":\"\",\"example_ru\":\"\"}]",
            "[{\"translation\":\"ЁЛКА\",\"example_de\":\"\",\"example_ru\":\"\"}]"
        )

        for (sample in russianSamples) {
            assertEquals(
                "Expected RU for sample: $sample",
                PartnerLanguageClassification.RU,
                DatabaseMigrations.classifyLanguage(sample)
            )
        }
    }

    @Test
    fun classifyLanguage_latinOnlyMeanings_returnsLatinAmbiguous() {
        val latinSamples = listOf(
            "[{\"translation\":\"house\",\"example_de\":\"Das Haus\",\"example_ru\":\"\"}]",
            "[{\"translation\":\"apple\",\"example_de\":\"Der Apfel\",\"example_ru\":\"\"}]",
            "[{\"translation\":\"carte\",\"example_de\":\"Das Buch\",\"example_ru\":\"\"}]"
        )

        for (sample in latinSamples) {
            assertEquals(
                "Expected LATIN_AMBIGUOUS for sample: $sample",
                PartnerLanguageClassification.LATIN_AMBIGUOUS,
                DatabaseMigrations.classifyLanguage(sample)
            )
        }
    }

    @Test
    fun classifyLanguage_emptyOrSymbols_returnsOther() {
        assertEquals(PartnerLanguageClassification.OTHER, DatabaseMigrations.classifyLanguage(""))
        assertEquals(PartnerLanguageClassification.OTHER, DatabaseMigrations.classifyLanguage("[]"))
        assertEquals(PartnerLanguageClassification.OTHER, DatabaseMigrations.classifyLanguage("12345 !?#"))
    }

    @Test
    fun classifyExistingWords_iteratesCursorAndUpdatesLanguages() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val cursor = mockk<Cursor>(relaxed = true)

        val rowData = listOf(
            1L to "[{\"translation\":\"дом\"}]", // RU
            2L to "[{\"translation\":\"поїзд\"}]", // UK
            3L to "[{\"translation\":\"پدر\"}]", // FA
            4L to "[{\"translation\":\"مدينة\"}]", // AR
            5L to "[{\"translation\":\"house\"}]", // LATIN_AMBIGUOUS
            6L to "[]" // OTHER -> ruCount
        )

        var currentIndex = -1
        every { cursor.moveToNext() } answers {
            currentIndex++
            currentIndex < rowData.size
        }
        every { cursor.getColumnIndex("id") } returns 0
        every { cursor.getColumnIndex("meaningsJson") } returns 1
        every { cursor.getLong(0) } answers { rowData[currentIndex].first }
        every { cursor.getString(1) } answers { rowData[currentIndex].second }

        every { db.query("SELECT id, meaningsJson FROM words") } returns cursor

        val stats = DatabaseMigrations.classifyExistingWords(db)

        assertEquals(6, stats.total)
        assertEquals(2, stats.ruCount) // дом + empty
        assertEquals(1, stats.ukCount)
        assertEquals(1, stats.faCount)
        assertEquals(1, stats.arCount)
        assertEquals(1, stats.latinAmbiguousCount)

        // Verify SQL updates for non-Russian rows
        verify(exactly = 1) { db.execSQL("UPDATE words SET partnerLanguage = 'uk' WHERE id = 2") }
        verify(exactly = 1) { db.execSQL("UPDATE words SET partnerLanguage = 'fa' WHERE id = 3") }
        verify(exactly = 1) { db.execSQL("UPDATE words SET partnerLanguage = 'ar' WHERE id = 4") }
        verify(exactly = 0) { db.execSQL("UPDATE words SET partnerLanguage = 'ru' WHERE id = 1") }
    }

    @Test
    fun migration4To5_executesSqlStatementsAndClassifies() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returns false
        every { db.query("SELECT id, meaningsJson FROM words") } returns cursor

        DatabaseMigrations.MIGRATION_4_5.migrate(db)

        verify { db.execSQL("ALTER TABLE words ADD COLUMN partnerLanguage TEXT NOT NULL DEFAULT 'ru'") }
        verify { db.execSQL("DROP INDEX IF EXISTS index_words_lemma_pos") }
        verify { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_words_lemma_pos_partnerLanguage ON words(lemma, pos, partnerLanguage)") }
        verify { db.query("SELECT id, meaningsJson FROM words") }
    }

    @Test
    fun schemasValidation_v4VsV5() {
        val schemasDir = File("schemas/app.deutschlern.data.local.AppDatabase")
        val schema4File = File(schemasDir, "4.json")
        val schema5File = File(schemasDir, "5.json")

        assertTrue("4.json must exist", schema4File.exists())
        assertTrue("5.json must exist", schema5File.exists())

        val schema4Content = schema4File.readText()
        val schema5Content = schema5File.readText()

        // Version 4 should have version 4 and index_words_lemma_pos
        assertTrue(schema4Content.contains("\"version\": 4"))
        assertTrue(schema4Content.contains("index_words_lemma_pos"))
        assertFalse(schema4Content.contains("index_words_lemma_pos_partnerLanguage"))

        // Version 5 should have version 5, partnerLanguage column, and updated index
        assertTrue(schema5Content.contains("\"version\": 5"))
        assertTrue(schema5Content.contains("\"columnName\": \"partnerLanguage\""))
        assertTrue(schema5Content.contains("\"defaultValue\": \"'ru'\""))
        assertTrue(schema5Content.contains("index_words_lemma_pos_partnerLanguage"))
    }

    @Test
    fun schemasValidation_v5VsV6() {
        val schemasDir = File("schemas/app.deutschlern.data.local.AppDatabase")
        val schema5File = File(schemasDir, "5.json")
        val schema6File = File(schemasDir, "6.json")

        assertTrue("5.json must exist", schema5File.exists())
        assertTrue("6.json must exist", schema6File.exists())

        val schema5Content = schema5File.readText()
        val schema6Content = schema6File.readText()

        // Version 5 does not have text_checks
        assertTrue(schema5Content.contains("\"version\": 5"))
        assertFalse(schema5Content.contains("text_checks"))

        // Version 6 should have version 6 and text_checks table
        assertTrue(schema6Content.contains("\"version\": 6"))
        assertTrue(schema6Content.contains("\"tableName\": \"text_checks\""))
        assertTrue(schema6Content.contains("index_text_checks_createdAt"))
    }

    @Test
    fun migration1To5_executesProperly() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returns false
        every { db.query(any<String>()) } returns cursor

        DatabaseMigrations.MIGRATION_1_5.migrate(db)

        verify { db.execSQL(match { it.contains("words") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `sessions`") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `session_words`") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `text_translations`") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `review_states`") }) }
    }

    @Test
    fun migration5To6_executesSqlStatements() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        DatabaseMigrations.MIGRATION_5_6.migrate(db)

        verify {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `text_checks`") })
        }
        verify {
            db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS `index_text_checks_createdAt` ON `text_checks`") })
        }
    }

    @Test
    fun migration1To6_executesProperly() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returns false
        every { db.query(any<String>()) } returns cursor

        DatabaseMigrations.MIGRATION_1_6.migrate(db)

        verify { db.execSQL(match { it.contains("words") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `text_checks`") }) }
    }

    @Test
    fun migration6To7_executesSqlStatements() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        DatabaseMigrations.MIGRATION_6_7.migrate(db)

        verify {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `dialogs`") })
        }
        verify {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `dialog_segments`") })
        }
        verify {
            db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS `index_dialog_segments_dialogId`") })
        }
        verify {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `dialog_tasks`") })
        }
        verify {
            db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS `index_dialog_tasks_dialogId`") })
        }
    }

    @Test
    fun migration5To7_executesProperly() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        DatabaseMigrations.MIGRATION_5_7.migrate(db)

        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `text_checks`") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `dialogs`") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `dialog_segments`") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `dialog_tasks`") }) }
    }

    @Test
    fun migration1To7_executesProperly() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returns false
        every { db.query(any<String>()) } returns cursor

        DatabaseMigrations.MIGRATION_1_7.migrate(db)

        verify { db.execSQL(match { it.contains("words") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `text_checks`") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `dialogs`") }) }
    }

    @Test
    fun migration4To7_skippingMultipleVersions_executesProperly() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returns false
        every { db.query(any<String>()) } returns cursor

        DatabaseMigrations.MIGRATION_4_7.migrate(db)

        verify { db.execSQL(match { it.contains("partnerLanguage") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `text_checks`") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `dialogs`") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `dialog_segments`") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `dialog_tasks`") }) }
    }

    @Test
    fun migration3To7_skippingMultipleVersions_executesProperly() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returns false
        every { db.query(any<String>()) } returns cursor

        DatabaseMigrations.MIGRATION_3_7.migrate(db)

        verify { db.execSQL(match { it.contains("words") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `text_checks`") }) }
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `dialogs`") }) }
    }

    @Test
    fun migration7To8_executesSqlStatements() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        DatabaseMigrations.MIGRATION_7_8.migrate(db)

        verify {
            db.execSQL(match { it.contains("ALTER TABLE dialogs ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETE'") })
        }
    }

    @Test
    fun schemasValidation_v7VsV8() {
        val schemasDir = File("schemas/app.deutschlern.data.local.AppDatabase")
        val schema7File = File(schemasDir, "7.json")
        val schema8File = File(schemasDir, "8.json")

        assertTrue("7.json must exist", schema7File.exists())
        assertTrue("8.json must exist", schema8File.exists())

        val schema7Content = schema7File.readText()
        val schema8Content = schema8File.readText()

        // Version 7 has version 7 and no status column in dialogs table createSql
        assertTrue(schema7Content.contains("\"version\": 7"))
        assertFalse(schema7Content.contains("`status` TEXT NOT NULL DEFAULT 'COMPLETE'"))

        // Version 8 has version 8, status column in dialogs with default 'COMPLETE'
        assertTrue(schema8Content.contains("\"version\": 8"))
        assertTrue(schema8Content.contains("`status` TEXT NOT NULL DEFAULT 'COMPLETE'"))
        assertTrue(schema8Content.contains("\"defaultValue\": \"'COMPLETE'\""))
    }
}
