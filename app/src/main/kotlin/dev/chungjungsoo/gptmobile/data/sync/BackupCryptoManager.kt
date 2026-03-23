package dev.chungjungsoo.gptmobile.data.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupCryptoManager @Inject constructor() {

    data class PasswordEncryptionResult(
        val cipherText: String,
        val salt: String,
        val iv: String,
        val iterations: Int
    )

    data class LocalEncryptionResult(
        val cipherText: String,
        val iv: String
    )

    fun encryptForBackup(plainText: String, password: String): PasswordEncryptionResult {
        val salt = ByteArray(SALT_SIZE).also(secureRandom::nextBytes)
        val iv = ByteArray(GCM_IV_SIZE).also(secureRandom::nextBytes)
        val key = derivePasswordKey(password, salt, PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return PasswordEncryptionResult(
            cipherText = cipherText.toBase64(),
            salt = salt.toBase64(),
            iv = iv.toBase64(),
            iterations = PBKDF2_ITERATIONS
        )
    }

    fun decryptBackup(cipherText: String, password: String, salt: String, iv: String, iterations: Int): String {
        val key = derivePasswordKey(password, salt.fromBase64(), iterations)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv.fromBase64()))
        val plainBytes = cipher.doFinal(cipherText.fromBase64())

        return plainBytes.toString(Charsets.UTF_8)
    }

    fun encryptForLocalStorage(plainText: String): LocalEncryptionResult {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateLocalSecretKey())
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return LocalEncryptionResult(
            cipherText = cipherBytes.toBase64(),
            iv = requireNotNull(cipher.iv).toBase64()
        )
    }

    fun decryptFromLocalStorage(cipherText: String, iv: String): String {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateLocalSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH, iv.fromBase64())
        )
        val plainBytes = cipher.doFinal(cipherText.fromBase64())

        return plainBytes.toString(Charsets.UTF_8)
    }

    private fun derivePasswordKey(password: String, salt: ByteArray, iterations: Int): SecretKey {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations, DERIVED_KEY_SIZE)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val secret = factory.generateSecret(keySpec).encoded

        return SecretKeySpec(secret, KEY_ALGORITHM)
    }

    private fun getOrCreateLocalSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(LOCAL_SECRET_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEY_STORE)
        val keySpec = KeyGenParameterSpec.Builder(
            LOCAL_SECRET_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(keySpec)

        return keyGenerator.generateKey()
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        private const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val DERIVED_KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_SIZE = 12
        private const val SALT_SIZE = 16
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val LOCAL_SECRET_ALIAS = "gptmobile_webdav_secret"
        private val secureRandom = SecureRandom()
    }
}
