package app.deutschlern.data.local.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.deutschlern.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.secureKeysDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_keys")

@Singleton
class SecureKeyStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "deutschlern_secure_master_key"
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    private val _isKeyRecoveryNeeded = MutableStateFlow(false)
    val isKeyRecoveryNeeded: StateFlow<Boolean> = _isKeyRecoveryNeeded.asStateFlow()

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Retrieves decrypted API key for the given provider.
     * Seeds Gemini API key from BuildConfig in DEBUG builds if no key exists.
     */
    suspend fun getKey(providerId: String): String? {
        val prefKey = stringPreferencesKey("enc_key_$providerId")
        val encryptedData = context.secureKeysDataStore.data.map { it[prefKey] }.first()

        if (encryptedData == null) {
            // Seed BuildConfig key for Gemini in DEBUG builds if present
            if (BuildConfig.DEBUG && providerId == "gemini" && BuildConfig.GEMINI_API_KEY.isNotBlank()) {
                saveKey("gemini", BuildConfig.GEMINI_API_KEY)
                return BuildConfig.GEMINI_API_KEY
            }
            return null
        }

        return decrypt(encryptedData, providerId)
    }

    suspend fun saveKey(providerId: String, key: String) {
        val trimmed = key.trim()
        val prefKey = stringPreferencesKey("enc_key_$providerId")
        if (trimmed.isEmpty()) {
            context.secureKeysDataStore.edit { it.remove(prefKey) }
            return
        }

        val encrypted = encrypt(trimmed)
        context.secureKeysDataStore.edit { prefs ->
            prefs[prefKey] = encrypted
        }
    }

    suspend fun deleteKey(providerId: String) {
        val prefKey = stringPreferencesKey("enc_key_$providerId")
        context.secureKeysDataStore.edit { it.remove(prefKey) }
    }

    fun observeHasKey(providerId: String): Flow<Boolean> {
        val prefKey = stringPreferencesKey("enc_key_$providerId")
        return context.secureKeysDataStore.data.map { prefs ->
            val hasStored = !prefs[prefKey].isNullOrBlank()
            if (!hasStored && BuildConfig.DEBUG && providerId == "gemini") {
                BuildConfig.GEMINI_API_KEY.isNotBlank()
            } else {
                hasStored
            }
        }
    }

    fun dismissRecoveryNotice() {
        _isKeyRecoveryNeeded.value = false
    }

    private fun encrypt(plainText: String): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return Base64.getEncoder().encodeToString(combined)
    }

    private suspend fun decrypt(encryptedBase64: String, providerId: String): String? {
        return try {
            val combined = Base64.getDecoder().decode(encryptedBase64)
            if (combined.size < GCM_IV_LENGTH) return null

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            when (e) {
                is KeyPermanentlyInvalidatedException,
                is GeneralSecurityException -> {
                    // Lost or invalidated hardware key: clear corrupted ciphertext and request re-entry
                    deleteKey(providerId)
                    _isKeyRecoveryNeeded.value = true
                    null
                }
                else -> {
                    deleteKey(providerId)
                    _isKeyRecoveryNeeded.value = true
                    null
                }
            }
        }
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        // Generate master key, trying StrongBox first, falling back to TEE
        return try {
            generateSecretKey(isStrongBox = true)
        } catch (e: Exception) {
            when (e) {
                is ProviderException,
                is GeneralSecurityException -> {
                    // StrongBox unavailable on this hardware, fall back to standard TEE
                    generateSecretKey(isStrongBox = false)
                }
                else -> generateSecretKey(isStrongBox = false)
            }
        }
    }

    private fun generateSecretKey(isStrongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val specBuilder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        if (isStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                specBuilder.setIsStrongBoxBacked(true)
            } catch (ignored: Throwable) {}
        }

        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Clears all encrypted stored keys and regenerates master key (e.g. for testing or recovery).
     */
    suspend fun resetAllKeys() {
        context.secureKeysDataStore.edit { it.clear() }
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            keyStore.deleteEntry(MASTER_KEY_ALIAS)
        }
        _isKeyRecoveryNeeded.value = false
    }
}
