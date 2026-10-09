package app.deutschlern.util

import java.security.MessageDigest

object CacheKeyGenerator {

    /**
     * Generates a 64-character lowercase SHA-256 hash key for Room translation cache:
     * providerId:modelName:promptVersion:mode:direction:normalizedText:context
     */
    fun generateKey(
        providerId: String,
        modelName: String,
        promptVersion: String,
        mode: String,
        direction: String,
        normalizedText: String,
        context: String? = null
    ): String {
        val raw = "$providerId:$modelName:$promptVersion:$mode:$direction:$normalizedText:${context.orEmpty()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a semantic hash key independent of provider and model:
     * promptVersion:mode:direction:normalizedText:context
     */
    fun generateSemanticKey(
        promptVersion: String,
        mode: String,
        direction: String,
        normalizedText: String,
        context: String? = null
    ): String {
        val raw = "$promptVersion:$mode:$direction:$normalizedText:${context.orEmpty()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a hash key for text check cache:
     * CHECK:providerId:modelName:promptVersion:partnerLanguage:normalizedText
     */
    fun generateCheckKey(
        providerId: String,
        modelName: String,
        promptVersion: String,
        partnerLanguage: String,
        normalizedText: String
    ): String {
        val raw = "CHECK:$providerId:$modelName:$promptVersion:$partnerLanguage:$normalizedText"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
