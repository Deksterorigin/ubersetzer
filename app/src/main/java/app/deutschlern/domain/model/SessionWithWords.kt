package app.deutschlern.domain.model

data class SessionWithWords(
    val session: Session,
    val words: List<WordEntry>
)
