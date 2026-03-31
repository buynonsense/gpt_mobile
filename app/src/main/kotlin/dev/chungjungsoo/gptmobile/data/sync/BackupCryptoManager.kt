package dev.chungjungsoo.gptmobile.data.sync

import javax.inject.Singleton

@Singleton
class BackupCryptoManager(
    private val localSecretCipher: LocalSecretCipher
) {

    constructor() : this(AndroidLocalSecretCipher())

    private val passwordCryptoHelper = PasswordCryptoHelper()

    fun encryptForBackup(plainText: String, password: String): PasswordCryptoHelper.EncryptionResult {
        return passwordCryptoHelper.encrypt(plainText, password)
    }

    fun decryptBackup(cipherText: String, password: String, salt: String, iv: String, iterations: Int): String {
        return passwordCryptoHelper.decrypt(cipherText, password, salt, iv, iterations)
    }

    fun encryptForLocalStorage(plainText: String): LocalSecretCipher.EncryptionResult = localSecretCipher.encrypt(plainText)

    fun decryptFromLocalStorage(cipherText: String, iv: String): String = localSecretCipher.decrypt(cipherText, iv)
}
