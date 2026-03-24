package dev.chungjungsoo.gptmobile.data.sync

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class PasswordCryptoHelper(
    private val secureRandom: SecureRandom = SecureRandom()
) {

    class InvalidBackupPasswordException(cause: Throwable? = null) :
        IllegalArgumentException("Invalid backup password", cause)

    data class EncryptionResult(
        val cipherText: String,
        val salt: String,
        val iv: String,
        val iterations: Int
    )

    fun encrypt(plainText: String, password: String): EncryptionResult {
        val salt = ByteArray(SALT_SIZE).also(secureRandom::nextBytes)
        val iv = ByteArray(GCM_IV_SIZE).also(secureRandom::nextBytes)
        val key = derivePasswordKey(password, salt, PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return EncryptionResult(
            cipherText = cipherText.toBase64(),
            salt = salt.toBase64(),
            iv = iv.toBase64(),
            iterations = PBKDF2_ITERATIONS
        )
    }

    fun decrypt(cipherText: String, password: String, salt: String, iv: String, iterations: Int): String {
        return try {
            val key = derivePasswordKey(password, salt.fromBase64(), iterations)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv.fromBase64()))
            val plainBytes = cipher.doFinal(cipherText.fromBase64())

            plainBytes.toString(Charsets.UTF_8)
        } catch (exception: IllegalArgumentException) {
            throw InvalidBackupPasswordException(exception)
        } catch (exception: AEADBadTagException) {
            throw InvalidBackupPasswordException(exception)
        } catch (exception: IllegalBlockSizeException) {
            throw InvalidBackupPasswordException(exception)
        }
    }

    private fun derivePasswordKey(password: String, salt: ByteArray, iterations: Int): SecretKey {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations, DERIVED_KEY_SIZE)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val secret = factory.generateSecret(keySpec).encoded

        return SecretKeySpec(secret, KEY_ALGORITHM)
    }

    private fun ByteArray.toBase64(): String = Base64.getEncoder().withoutPadding().encodeToString(this)

    private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)

    companion object {
        private const val KEY_ALGORITHM = "AES"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val DERIVED_KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_SIZE = 12
        private const val SALT_SIZE = 16
    }
}
