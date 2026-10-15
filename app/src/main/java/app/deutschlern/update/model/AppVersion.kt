package app.deutschlern.update.model

/**
 * Robust semantic version parser and comparator.
 *
 * Supports:
 * - Leading 'v' or 'V' (e.g., "v1.2.3")
 * - 1, 2, or 3+ numeric segments (e.g., "1.0", "1.0.0", "2.1.4.5")
 * - Pre-release tags (e.g., "1.0.0-beta.1", "1.0.0-rc1")
 * - Proper numeric comparison: 1.10 is greater than 1.9
 */
data class AppVersion(
    val raw: String,
    val segments: List<Int>,
    val preRelease: String? = null
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val maxLen = maxOf(this.segments.size, other.segments.size)
        for (i in 0 until maxLen) {
            val s1 = this.segments.getOrElse(i) { 0 }
            val s2 = other.segments.getOrElse(i) { 0 }
            if (s1 != s2) {
                return s1.compareTo(s2)
            }
        }

        // A version without pre-release is greater than one with pre-release: 1.0.0 > 1.0.0-beta
        return when {
            this.preRelease == null && other.preRelease == null -> 0
            this.preRelease == null && other.preRelease != null -> 1
            this.preRelease != null && other.preRelease == null -> -1
            else -> this.preRelease!!.compareTo(other.preRelease!!)
        }
    }

    override fun toString(): String = raw

    companion object {
        fun parseOrNull(value: String): AppVersion? {
            val trimmed = value.trim()
            if (trimmed.isBlank()) return null

            // Strip leading 'v' or 'V'
            val withoutPrefix = if (trimmed.startsWith("v", ignoreCase = true)) {
                trimmed.substring(1).trim()
            } else {
                trimmed
            }

            if (withoutPrefix.isBlank()) return null

            // Split pre-release / build metadata (e.g., "1.0.0-beta" or "1.0.0+2026")
            val preReleaseIndex = withoutPrefix.indexOfAny(charArrayOf('-', '+'))
            val versionCore: String
            val preRelease: String?

            if (preReleaseIndex >= 0) {
                versionCore = withoutPrefix.substring(0, preReleaseIndex).trim()
                preRelease = withoutPrefix.substring(preReleaseIndex + 1).trim()
            } else {
                versionCore = withoutPrefix
                preRelease = null
            }

            if (versionCore.isBlank()) return null

            val parts = versionCore.split('.')
            val numericSegments = mutableListOf<Int>()
            for (part in parts) {
                val num = part.trim().toIntOrNull() ?: return null
                if (num < 0) return null
                numericSegments.add(num)
            }

            if (numericSegments.isEmpty()) return null

            return AppVersion(
                raw = trimmed,
                segments = numericSegments,
                preRelease = preRelease
            )
        }

        fun parse(value: String): AppVersion {
            return parseOrNull(value) ?: throw IllegalArgumentException("Malformed version string: $value")
        }
    }
}
