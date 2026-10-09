package app.deutschlern.domain.model

data class Session(
    val id: Long = 0,
    val title: String,
    val date: Long,
    val isManual: Boolean = false
)
