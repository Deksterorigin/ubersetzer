package app.deutschlern.domain.exam

/** A piece of a gap-fill text: plain text, or the gap that question [number] fills. */
sealed interface GapToken {
    data class Text(val text: String) : GapToken
    data class Gap(val number: Int) : GapToken
}

/** Splits passage bodies on the {{46}} gap markers used by the Sprachbausteine content. */
object GapText {

    private val MARKER = Regex("""\{\{\s*(\d+)\s*\}\}""")

    fun hasGaps(body: String): Boolean = MARKER.containsMatchIn(body)

    fun gapNumbers(body: String): List<Int> = MARKER.findAll(body).map { it.groupValues[1].toInt() }.toList()

    fun parse(body: String): List<GapToken> {
        val tokens = mutableListOf<GapToken>()
        var last = 0
        for (match in MARKER.findAll(body)) {
            if (match.range.first > last) tokens += GapToken.Text(body.substring(last, match.range.first))
            tokens += GapToken.Gap(match.groupValues[1].toInt())
            last = match.range.last + 1
        }
        if (last < body.length) tokens += GapToken.Text(body.substring(last))
        return tokens
    }
}
