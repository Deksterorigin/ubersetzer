package app.deutschlern.domain.model

/**
 * Fixed set of themes a word can be filed under. [key] is what is stored in the database
 * and what the classifier model must return, so keys must never be renamed.
 */
enum class WordTopic(val key: String) {
    WORK("work"),
    BUREAUCRACY("bureaucracy"),
    FINANCE("finance"),
    EVERYDAY("everyday"),
    HOUSING("housing"),
    FOOD_SHOPPING("food_shopping"),
    HEALTH("health"),
    TRAVEL("travel"),
    EDUCATION("education"),
    FEELINGS("feelings"),
    NATURE("nature"),
    TECHNOLOGY("technology");

    companion object {
        fun fromKey(key: String): WordTopic? = entries.firstOrNull { it.key == key }
    }
}
