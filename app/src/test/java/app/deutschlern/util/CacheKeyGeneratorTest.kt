package app.deutschlern.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheKeyGeneratorTest {

    private val providerId = "gemini"
    private val model = "gemini-3.5-flash"
    private val promptVersion = "v1.0"
    private val mode = "TEXT"
    private val direction = "DE_RU"

    @Test
    fun generateKey_returnsValidSha256Hex() {
        val key = CacheKeyGenerator.generateKey(
            providerId = providerId,
            modelName = model,
            promptVersion = promptVersion,
            mode = mode,
            direction = direction,
            normalizedText = "Guten Morgen"
        )

        assertEquals(64, key.length)
        assertTrue(key.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun generateKey_isDeterministic() {
        val key1 = CacheKeyGenerator.generateKey(
            providerId = providerId,
            modelName = model,
            promptVersion = promptVersion,
            mode = mode,
            direction = direction,
            normalizedText = "Hallo Welt"
        )

        val key2 = CacheKeyGenerator.generateKey(
            providerId = providerId,
            modelName = model,
            promptVersion = promptVersion,
            mode = mode,
            direction = direction,
            normalizedText = "Hallo Welt"
        )

        assertEquals(key1, key2)
    }

    @Test
    fun generateKey_preservesCaseSensitivity() {
        val keyNoun = CacheKeyGenerator.generateKey(
            providerId = providerId,
            modelName = model,
            promptVersion = promptVersion,
            mode = mode,
            direction = direction,
            normalizedText = "Schloss"
        )

        val keyVerb = CacheKeyGenerator.generateKey(
            providerId = providerId,
            modelName = model,
            promptVersion = promptVersion,
            mode = mode,
            direction = direction,
            normalizedText = "schloss"
        )

        assertNotEquals(keyNoun, keyVerb)
    }

    @Test
    fun generateSemanticKey_isSameAcrossProvidersAndModels() {
        val semKey1 = CacheKeyGenerator.generateSemanticKey(promptVersion, mode, direction, "Haus", null)
        val semKey2 = CacheKeyGenerator.generateSemanticKey(promptVersion, mode, direction, "Haus", null)
        assertEquals(semKey1, semKey2)

        val exactKeyGemini = CacheKeyGenerator.generateKey("gemini", "gemini-3.5-flash", promptVersion, mode, direction, "Haus")
        val exactKeyGroq = CacheKeyGenerator.generateKey("groq", "llama-3.3-70b", promptVersion, mode, direction, "Haus")

        // Exact keys differ by provider/model
        assertNotEquals(exactKeyGemini, exactKeyGroq)
    }

    @Test
    fun generateKey_differentParameters_produceDifferentKeys() {
        val keyBase = CacheKeyGenerator.generateKey(providerId, model, promptVersion, mode, direction, "Haus")
        val keyDiffProvider = CacheKeyGenerator.generateKey("groq", model, promptVersion, mode, direction, "Haus")
        val keyDiffModel = CacheKeyGenerator.generateKey(providerId, "gemini-1.5-flash", promptVersion, mode, direction, "Haus")
        val keyDiffPrompt = CacheKeyGenerator.generateKey(providerId, model, "v2.0", mode, direction, "Haus")
        val keyDiffMode = CacheKeyGenerator.generateKey(providerId, model, promptVersion, "WORD", direction, "Haus")
        val keyDiffDir = CacheKeyGenerator.generateKey(providerId, model, promptVersion, mode, "RU_DE", "Haus")
        val keyDiffContext = CacheKeyGenerator.generateKey(providerId, model, promptVersion, mode, direction, "Haus", context = "im Haus")

        assertNotEquals(keyBase, keyDiffProvider)
        assertNotEquals(keyBase, keyDiffModel)
        assertNotEquals(keyBase, keyDiffPrompt)
        assertNotEquals(keyBase, keyDiffMode)
        assertNotEquals(keyBase, keyDiffDir)
        assertNotEquals(keyBase, keyDiffContext)
    }

    @Test
    fun generateSemanticKey_differentPartnerLanguages_produceDifferentKeys() {
        val word = "Haus"
        val keyDeRu = CacheKeyGenerator.generateSemanticKey(promptVersion, mode, app.deutschlern.domain.model.TranslationDirection.DE_RU.name, word)
        val keyDeEn = CacheKeyGenerator.generateSemanticKey(promptVersion, mode, app.deutschlern.domain.model.TranslationDirection.DE_EN.name, word)
        val keyDeUk = CacheKeyGenerator.generateSemanticKey(promptVersion, mode, app.deutschlern.domain.model.TranslationDirection.DE_UK.name, word)
        val keyDeAr = CacheKeyGenerator.generateSemanticKey(promptVersion, mode, app.deutschlern.domain.model.TranslationDirection.DE_AR.name, word)
        val keyDeFa = CacheKeyGenerator.generateSemanticKey(promptVersion, mode, app.deutschlern.domain.model.TranslationDirection.DE_FA.name, word)
        val keyDeRo = CacheKeyGenerator.generateSemanticKey(promptVersion, mode, app.deutschlern.domain.model.TranslationDirection.DE_RO.name, word)

        // Ensure DE->RU cached translation is NEVER returned for DE->EN or any other partner language
        assertNotEquals("DE_RU must differ from DE_EN", keyDeRu, keyDeEn)
        assertNotEquals("DE_RU must differ from DE_UK", keyDeRu, keyDeUk)
        assertNotEquals("DE_RU must differ from DE_AR", keyDeRu, keyDeAr)
        assertNotEquals("DE_RU must differ from DE_FA", keyDeRu, keyDeFa)
        assertNotEquals("DE_RU must differ from DE_RO", keyDeRu, keyDeRo)
        assertNotEquals("DE_EN must differ from DE_UK", keyDeEn, keyDeUk)
    }
}
