package app.deutschlern.domain.ocr

object OcrTextAssembler {

    fun assemble(pages: List<OcrPageSelection>): String {
        val raw = pages
            .map { pageText(it) }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
        return OcrPostProcessor.process(raw)
    }

    private fun pageText(page: OcrPageSelection): String {
        val selected = page.words.filter { it.id in page.selectedIds }
        if (selected.isEmpty()) return ""

        val wordsByLine = page.words.groupBy { it.lineIndex }
        val text = StringBuilder(selected.first().text)
        selected.zipWithNext { previous, word ->
            text.append(separator(previous, word, wordsByLine)).append(word.text)
        }
        return text.toString()
    }

    private fun separator(
        previous: OcrWord,
        word: OcrWord,
        wordsByLine: Map<Int, List<OcrWord>>
    ): String = when {
        previous.blockIndex != word.blockIndex -> "\n\n"
        previous.lineIndex == word.lineIndex -> " "
        // A newline would let the post-processor glue "Spra-" onto a word that is not its ending
        OcrPostProcessor.endsWithHyphen(previous.text) &&
            !continuesOnNextLine(previous, word, wordsByLine) -> " "
        else -> "\n"
    }

    private fun continuesOnNextLine(
        previous: OcrWord,
        word: OcrWord,
        wordsByLine: Map<Int, List<OcrWord>>
    ): Boolean = word.lineIndex == previous.lineIndex + 1 &&
        wordsByLine[previous.lineIndex]?.lastOrNull()?.id == previous.id &&
        wordsByLine[word.lineIndex]?.firstOrNull()?.id == word.id
}
