package dev.chungjungsoo.gptmobile.data.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupCryptoManager @Inject constructor() {

    private val passwordCryptoHelper = PasswordCryptoHelper()

    data class LocalEncryptionResult(
        val cipherText: String,
        val iv: String
    )

    fun encryptForBackup(plainText: String, password: String): PasswordCryptoHelper.EncryptionResult {
        return passwordCryptoHelper.encrypt(plainText, password)
    }

    fun decryptBackup(cipherText: String, password: String, salt: String, iv: String, iterations: Int): String {
        return passwordCryptoHelper.decrypt(cipherText, password, salt, iv, iterations)
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

    private fun ByteArray.toBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)

    companion object {
        private const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val LOCAL_SECRET_ALIAS = "gptmobile_webdav_secret"
    }
}
