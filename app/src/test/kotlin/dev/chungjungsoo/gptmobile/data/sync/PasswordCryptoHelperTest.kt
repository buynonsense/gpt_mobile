package dev.chungjungsoo.gptmobile.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PasswordCryptoHelperTest {
    private val helper = PasswordCryptoHelper()

    @Test
    fun decrypt_returnsOriginalPlainText_whenUsingSamePassword() {
        val encrypted = helper.encrypt("plain-text", "correct-password")

        val plainText = helper.decrypt(
            cipherText = encrypted.cipherText,
            password = "correct-password",
            salt = encrypted.salt,
            iv = encrypted.iv,
            iterations = encrypted.iterations
        )

        assertEquals("plain-text", plainText)
    }

    @Test
    fun decrypt_throws_whenPasswordIsWrong() {
        val encrypted = helper.encrypt("plain-text", "correct-password")

        try {
            helper.decrypt(
                cipherText = encrypted.cipherText,
                password = "wrong-password",
                salt = encrypted.salt,
                iv = encrypted.iv,
                iterations = encrypted.iterations
            )
            fail("使用错误密码解密应失败")
        } catch (exception: IllegalArgumentException) {
            assertEquals("Invalid backup password", exception.message)
        }
    }

    @Test
    fun decrypt_throwsConsistentException_whenCipherTextIsInvalidBase64() {
        return try {
            helper.decrypt(
                cipherText = "not-base64!",
                password = "any-password",
                salt = "not-base64!",
                iv = "not-base64!",
                iterations = 120_000
            )
            fail("非法输入应映射为统一业务异常")
        } catch (exception: IllegalArgumentException) {
            assertEquals("Invalid backup password", exception.message)
            assertTrue(exception.cause == null || exception.cause is IllegalArgumentException)
        }
    }
}
