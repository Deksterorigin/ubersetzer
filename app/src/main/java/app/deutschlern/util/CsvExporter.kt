package app.deutschlern.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.deutschlern.domain.model.WordEntry
import java.io.File

object CsvExporter {

    /**
     * Generates RFC 4180 compliant CSV formatted for Anki import:
     * Front: "article lemma" or "lemma"
     * Back: translation (note) + exampleDe + exampleRu
     */
    fun generateAnkiCsv(words: List<WordEntry>): String {
        val sb = StringBuilder()
        sb.append("#separator:Comma\r\n")
        sb.append("#html:true\r\n")
        sb.append("\"Front\",\"Back\"\r\n")

        for (word in words) {
            val front = if (!word.article.isNullOrBlank()) "${word.article} ${word.lemma}" else word.lemma
            val meaning = word.selectedMeaning ?: word.meanings.firstOrNull()
            val translation = meaning?.translation.orEmpty()
            val note = if (!meaning?.note.isNullOrBlank()) " (${meaning?.note})" else ""
            val exDe = meaning?.exampleDe.orEmpty()
            val exRu = meaning?.exampleRu.orEmpty()

            val back = buildString {
                append(translation).append(note)
                if (exDe.isNotBlank()) {
                    append("<br><br><i>").append(exDe).append("</i>")
                    if (exRu.isNotBlank()) {
                        append("<br>").append(exRu)
                    }
                }
            }

            sb.append(escapeRfc4180(front))
                .append(",")
                .append(escapeRfc4180(back))
                .append("\r\n")
        }

        return sb.toString()
    }

    fun escapeRfc4180(field: String): String {
        val escaped = field.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /**
     * Writes Anki CSV into cache directory and triggers the Android Sharesheet.
     */
    fun shareAnkiCsv(context: Context, words: List<WordEntry>, fileName: String = "deutschlern_anki.csv") {
        if (words.isEmpty()) return
        val csv = generateAnkiCsv(words)
        val exportDir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(exportDir, fileName)
        file.writeText(csv, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "DeutschLern Anki Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Экспорт слов в Anki (CSV)")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
