package app.deutschlern.domain.model

enum class WordStatus {
    NEW,
    LEARNING,
    KNOWN;

    companion object {
        fun fromString(value: String): WordStatus {
            return when (value.uppercase()) {
                "NEW" -> NEW
                "LEARNING" -> LEARNING
                "KNOWN" -> KNOWN
                else -> NEW
            }
        }
    }
}
