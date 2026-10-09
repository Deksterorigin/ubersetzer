package app.deutschlern.domain.dialog

object GapFillEvaluator {

    fun isAnswerCorrect(
        userAnswer: String,
        expectedAnswer: String,
        alternatives: List<String> = emptyList()
    ): Boolean {
        val normalizedUser = normalize(userAnswer)
        if (normalizedUser.isBlank()) return false

        val normalizedExpected = normalize(expectedAnswer)
        if (normalizedUser == normalizedExpected) return true

        for (alt in alternatives) {
            if (normalizedUser == normalize(alt)) return true
        }

        // Check umlaut-expanded equivalence (ae <-> ä, etc.)
        val userExpanded = expandUmlauts(normalizedUser)
        val expectedExpanded = expandUmlauts(normalizedExpected)
        if (userExpanded == expectedExpanded) return true

        for (alt in alternatives) {
            if (userExpanded == expandUmlauts(normalize(alt))) return true
        }

        return false
    }

    private fun normalize(str: String): String {
        return str.trim()
            .lowercase()
            .replace(Regex("""[.,!?;:\"'«»]"""), "")
            .replace(Regex("""\s+"""), " ")
    }

    private fun expandUmlauts(str: String): String {
        return str
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
    }
}
