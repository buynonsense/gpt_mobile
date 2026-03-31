package dev.chungjungsoo.gptmobile.data.sync

import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.StreamingStyle
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.sync.model.BackupEncryption
import dev.chungjungsoo.gptmobile.data.sync.model.BackupFile
import dev.chungjungsoo.gptmobile.data.sync.model.BackupSummary
import dev.chungjungsoo.gptmobile.data.sync.model.SyncConflict
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavRemoteFile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepositoryImplTest {
    private val fakeBackupRepository = FakeBackupRepository()
    private val fakeWebDavRepository = FakeWebDavRepository()
    private val fakeSettingRepository = FakeSettingRepository()
    private val fakeLocalSecretCipher = FakeLocalSecretCipher()
    private val repository = SyncRepositoryImpl(
        backupRepository = fakeBackupRepository,
        webDavRepository = fakeWebDavRepository,
        settingRepository = fakeSettingRepository,
        cryptoManager = BackupCryptoManager(fakeLocalSecretCipher)
    )

    @Test
    fun testWebDavConnection_normalizesBaseUrlAndRemotePath() = runBlocking {
        repository.testWebDavConnection(
            baseUrl = "https://dav.example.com///",
            username = " user ",
            remotePath = "/backup/path/",
            password = "pw"
        )

        assertEquals("https://dav.example.com", fakeWebDavRepository.testedConfig?.baseUrl)
        assertEquals("user", fakeWebDavRepository.testedConfig?.username)
        assertEquals("backup/path", fakeWebDavRepository.testedConfig?.remotePath)
        assertEquals("pw", fakeWebDavRepository.testedPassword)
    }

    @Test
    fun getWebDavConfig_returnsStoredConfig() = runBlocking {
        val config = WebDavConfig(
            baseUrl = "https://dav.example.com",
            username = "user",
            remotePath = "backup"
        )
        fakeSettingRepository.savedConfig = config

        assertEquals(config, repository.getWebDavConfig())
    }

    @Test
    fun clearWebDavConfig_clearsStoredConfig() = runBlocking {
        fakeSettingRepository.savedConfig = WebDavConfig(
            baseUrl = "https://dav.example.com",
            username = "user",
            remotePath = "backup"
        )

        repository.clearWebDavConfig()

        assertNull(fakeSettingRepository.savedConfig)
    }

    @Test
    fun listRemoteBackups_withoutConfig_throws() {
        fakeSettingRepository.savedConfig = null

        assertThrowsWithMessageFragment("WebDAV config") {
            runBlocking {
                repository.listRemoteBackups("pw")
            }
        }
    }

    @Test
    fun uploadBackup_withoutConfig_throws() {
        fakeSettingRepository.savedConfig = null

        assertThrowsWithMessageFragment("WebDAV config") {
            runBlocking {
                repository.uploadBackup(password = "pw", overwrite = true)
            }
        }
    }

    @Test
    fun downloadRemoteBackup_withoutConfig_throws() {
        fakeSettingRepository.savedConfig = null

        assertThrowsWithMessageFragment("WebDAV config") {
            runBlocking {
                repository.downloadRemoteBackup(password = "pw", remoteFileName = "remote.json")
            }
        }
    }

    @Test
    fun detectUploadConflict_withoutRemoteBackups_returnsNull() = runBlocking {
        fakeSettingRepository.savedConfig = storedConfig()
        fakeWebDavRepository.remoteFiles = emptyList()

        assertNull(repository.detectUploadConflict("pw"))
    }

    @Test
    fun detectUploadConflict_withNewerRemoteBackup_returnsConflict() = runBlocking {
        fakeSettingRepository.savedConfig = storedConfig()
        fakeWebDavRepository.remoteFiles = listOf(remoteFile(name = "remote.json", modifiedAt = "2026-03-31T10:00:00Z"))
        fakeWebDavRepository.downloadedContent = fakeBackupRepository.backupJson(
            exportedAt = 200L,
            chatRoomCount = 2
        )

        val conflict = repository.detectUploadConflict("pw")

        assertNotNull(conflict)
        assertConflict(
            conflict = conflict,
            remoteFileName = "remote.json",
            localChatRoomCount = 1,
            remoteChatRoomCount = 2,
            localExportedAt = 100L,
            remoteExportedAt = 200L
        )
    }

    @Test
    fun detectUploadConflict_withOlderRemoteBackup_returnsNull() = runBlocking {
        fakeSettingRepository.savedConfig = storedConfig()
        fakeWebDavRepository.remoteFiles = listOf(remoteFile(name = "remote.json", modifiedAt = "2026-03-31T10:00:00Z"))
        fakeWebDavRepository.downloadedContent = fakeBackupRepository.backupJson(
            exportedAt = 50L,
            chatRoomCount = 2
        )

        assertNull(repository.detectUploadConflict("pw"))
    }

    @Test
    fun detectUploadConflict_withCorruptedRemoteBackup_throws() {
        fakeSettingRepository.savedConfig = storedConfig()
        fakeWebDavRepository.remoteFiles = listOf(remoteFile(name = "remote.json", modifiedAt = "2026-03-31T10:00:00Z"))
        fakeWebDavRepository.downloadedContent = "not-json"

        assertThrows(SerializationException::class.java) {
            runBlocking {
                repository.detectUploadConflict("pw")
            }
        }
    }

    @Test
    fun getWebDavPassword_returnsDecryptedPasswordFromCipherSeam() = runBlocking {
        fakeSettingRepository.savedConfig = storedConfig().copy(
            encryptedPassword = "encrypted:pw",
            passwordIv = "fake-iv"
        )

        val password = repository.getWebDavPassword()

        assertEquals("pw", password)
        assertEquals("encrypted:pw", fakeLocalSecretCipher.decryptedCipherText)
        assertEquals("fake-iv", fakeLocalSecretCipher.decryptedIv)
    }

    @Test
    fun getWebDavPassword_returnsNull_whenStoredPasswordMetadataIsIncomplete() = runBlocking {
        fakeSettingRepository.savedConfig = storedConfig().copy(
            encryptedPassword = null,
            passwordIv = "fake-iv"
        )

        assertNull(repository.getWebDavPassword())
    }

    @Test
    fun getWebDavPassword_returnsNull_whenStoredPasswordIvIsMissing() = runBlocking {
        fakeSettingRepository.savedConfig = storedConfig().copy(
            encryptedPassword = "encrypted:pw",
            passwordIv = null
        )

        assertNull(repository.getWebDavPassword())
    }

    @Test
    fun getWebDavPassword_returnsNull_whenConfigIsMissing() = runBlocking {
        fakeSettingRepository.savedConfig = null

        assertNull(repository.getWebDavPassword())
    }

    @Test
    fun detectUploadConflict_withUnsupportedRemoteSchema_throws() {
        fakeSettingRepository.savedConfig = storedConfig()
        fakeWebDavRepository.remoteFiles = listOf(remoteFile(name = "remote.json", modifiedAt = "2026-03-31T10:00:00Z"))
        fakeWebDavRepository.downloadedContent = fakeBackupRepository.backupJson(
            schemaVersion = 99,
            exportedAt = 200L,
            chatRoomCount = 2
        )

        assertThrowsWithMessageFragment("Unsupported backup schema version") {
            runBlocking {
                repository.detectUploadConflict("pw")
            }
        }
    }

    @Test
    fun uploadBackup_withoutOverwrite_whenConflictExists_throws() {
        fakeSettingRepository.savedConfig = storedConfig()
        fakeWebDavRepository.remoteFiles = listOf(remoteFile(name = "remote.json", modifiedAt = "2026-03-31T10:00:00Z"))
        fakeWebDavRepository.downloadedContent = fakeBackupRepository.backupJson(
            exportedAt = 200L,
            chatRoomCount = 2
        )

        assertThrowsWithMessageFragment("remote.json") {
            runBlocking {
                repository.uploadBackup(password = "pw", overwrite = false)
            }
        }
        assertTrue(fakeWebDavRepository.uploadCalls.isEmpty())
    }

    @Test
    fun uploadBackup_withOverwrite_uploadsBackup() = runBlocking {
        fakeSettingRepository.savedConfig = storedConfig()

        val fileName = repository.uploadBackup(password = "pw", overwrite = true)

        assertEquals(1, fakeWebDavRepository.uploadCalls.size)
        assertTrue(fileName.startsWith("gptmobile-backup-"))
        assertTrue(fileName.endsWith(".json"))
        assertEquals(fileName, fakeWebDavRepository.uploadCalls.single().fileName)
    }

    @Test
    fun downloadRemoteBackup_returnsRemoteContent() = runBlocking {
        fakeSettingRepository.savedConfig = storedConfig()
        fakeWebDavRepository.downloadedContent = fakeBackupRepository.backupJson(
            exportedAt = 200L,
            chatRoomCount = 2
        )

        val content = repository.downloadRemoteBackup("pw", "remote.json")

        assertEquals(fakeWebDavRepository.downloadedContent, content)
        assertEquals("remote.json", fakeWebDavRepository.downloadedRemotePath)
    }

    @Test
    fun saveWebDavConfig_persistsNormalizedConfig() = runBlocking {
        repository.saveWebDavConfig(
            baseUrl = "https://dav.example.com/",
            username = " user ",
            remotePath = "/path/",
            password = "pw"
        )

        assertEquals("https://dav.example.com", fakeSettingRepository.savedConfig?.baseUrl)
        assertEquals("user", fakeSettingRepository.savedConfig?.username)
        assertEquals("path", fakeSettingRepository.savedConfig?.remotePath)
        assertEquals("encrypted:pw", fakeSettingRepository.savedConfig?.encryptedPassword)
        assertEquals("fake-iv", fakeSettingRepository.savedConfig?.passwordIv)
    }

    private fun assertConflict(
        conflict: SyncConflict?,
        remoteFileName: String,
        localChatRoomCount: Int,
        remoteChatRoomCount: Int,
        localExportedAt: Long,
        remoteExportedAt: Long
    ) {
        assertEquals(remoteFileName, conflict?.remoteFileName)
        assertEquals(localChatRoomCount, conflict?.localSummary?.chatRoomCount)
        assertEquals(remoteChatRoomCount, conflict?.remoteSummary?.chatRoomCount)
        assertEquals(localExportedAt, conflict?.localExportedAt)
        assertEquals(remoteExportedAt, conflict?.remoteExportedAt)
    }

    private fun assertThrowsWithMessageFragment(
        messageFragment: String,
        block: () -> Unit
    ): Throwable {
        val exception = assertThrows(Throwable::class.java, block)
        assertTrue(exception.message.orEmpty().contains(messageFragment))
        return exception
    }

    private fun storedConfig(): WebDavConfig = WebDavConfig(
        baseUrl = "https://dav.example.com",
        username = "user",
        remotePath = "backup"
    )

    private fun remoteFile(name: String, modifiedAt: String): WebDavRemoteFile = WebDavRemoteFile(
        path = "/backup/$name",
        name = name,
        modifiedAt = modifiedAt
    )

    private class FakeBackupRepository : BackupRepository {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private val localBackup = backupFile(exportedAt = 100L, chatRoomCount = 1)

        override suspend fun exportBackup(password: String): BackupFile = localBackup

        override suspend fun restoreBackup(fileContent: String, password: String) {
            throw UnsupportedOperationException("restoreBackup is not used in this test")
        }

        override suspend fun parseBackupFile(fileContent: String): BackupFile {
            val backup = json.decodeFromString<BackupFile>(fileContent)
            require(backup.schemaVersion == 1) {
                "Unsupported backup schema version: ${backup.schemaVersion}"
            }
            return backup
        }

        override suspend fun readSummary(fileContent: String): BackupSummary = parseBackupFile(fileContent).summary

        fun backupJson(
            schemaVersion: Int = 1,
            exportedAt: Long,
            chatRoomCount: Int
        ): String = json.encodeToString(backupFile(schemaVersion, exportedAt, chatRoomCount))

        private fun backupFile(
            schemaVersion: Int = 1,
            exportedAt: Long,
            chatRoomCount: Int
        ): BackupFile = BackupFile(
            schemaVersion = schemaVersion,
            exportedAt = exportedAt,
            appVersion = "0.9.6",
            backupType = "full",
            summary = BackupSummary(
                chatRoomCount = chatRoomCount,
                messageCount = 0,
                aiMaskCount = 0,
                containsSecrets = false
            ),
            encryption = BackupEncryption(
                enabled = true,
                algorithm = "AES/GCM/NoPadding",
                kdf = "PBKDF2WithHmacSHA256",
                iterations = 1,
                salt = "salt",
                iv = "iv"
            ),
            payload = "payload"
        )
    }

    private class FakeWebDavRepository : WebDavRepository {
        var testedConfig: WebDavConfig? = null
        var testedPassword: String? = null
        var remoteFiles: List<WebDavRemoteFile> = emptyList()
        var downloadedContent: String = ""
        var downloadedRemotePath: String? = null
        val uploadCalls = mutableListOf<UploadCall>()

        override suspend fun testConnection(config: WebDavConfig, password: String) {
            testedConfig = config
            testedPassword = password
        }

        override suspend fun listBackupFiles(config: WebDavConfig, password: String): List<WebDavRemoteFile> = remoteFiles

        override suspend fun uploadBackup(config: WebDavConfig, password: String, fileName: String, content: String) {
            uploadCalls += UploadCall(config = config, password = password, fileName = fileName, content = content)
        }

        override suspend fun downloadBackup(config: WebDavConfig, password: String, remotePath: String): String {
            downloadedRemotePath = remotePath
            return downloadedContent
        }
    }

    private data class UploadCall(
        val config: WebDavConfig,
        val password: String,
        val fileName: String,
        val content: String
    )

    private class FakeSettingRepository : SettingRepository {
        var savedConfig: WebDavConfig? = null

        override suspend fun fetchPlatforms(): List<Platform> {
            throw UnsupportedOperationException("fetchPlatforms is not used in this test")
        }

        override suspend fun fetchThemes(): ThemeSetting {
            throw UnsupportedOperationException("fetchThemes is not used in this test")
        }

        override suspend fun fetchStreamingStyle(): StreamingStyle {
            throw UnsupportedOperationException("fetchStreamingStyle is not used in this test")
        }

        override suspend fun fetchWebDavConfig(): WebDavConfig? = savedConfig

        override suspend fun updatePlatforms(platforms: List<Platform>) {
            throw UnsupportedOperationException("updatePlatforms is not used in this test")
        }

        override suspend fun updateThemes(themeSetting: ThemeSetting) {
            throw UnsupportedOperationException("updateThemes is not used in this test")
        }

        override suspend fun updateStreamingStyle(style: StreamingStyle) {
            throw UnsupportedOperationException("updateStreamingStyle is not used in this test")
        }

        override suspend fun updateWebDavConfig(config: WebDavConfig?) {
            savedConfig = config
        }
    }

    private class FakeLocalSecretCipher : LocalSecretCipher {
        var decryptedCipherText: String? = null
        var decryptedIv: String? = null

        override fun encrypt(plainText: String): LocalSecretCipher.EncryptionResult {
            return LocalSecretCipher.EncryptionResult(
                cipherText = "encrypted:$plainText",
                iv = "fake-iv"
            )
        }

        override fun decrypt(cipherText: String, iv: String): String {
            decryptedCipherText = cipherText
            decryptedIv = iv
            return cipherText.removePrefix("encrypted:")
        }
    }
}
