package dev.chungjungsoo.gptmobile.data.sync

import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.sync.model.BackupFile
import dev.chungjungsoo.gptmobile.data.sync.model.SyncConflict
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavRemoteFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val backupRepository: BackupRepository,
    private val webDavRepository: WebDavRepository,
    private val settingRepository: SettingRepository,
    private val cryptoManager: BackupCryptoManager
) : SyncRepository {

    override suspend fun exportBackupJson(): String {
        val backup = backupRepository.exportBackup()
        return json.encodeToString(BackupFile.serializer(), backup)
    }

    override suspend fun restoreBackupJson(content: String) {
        backupRepository.restoreBackup(content)
    }

    override suspend fun parseBackup(content: String): BackupFile = backupRepository.parseBackupFile(content)

    override suspend fun testWebDavConnection(baseUrl: String, username: String, remotePath: String, password: String) {
        webDavRepository.testConnection(
            config = WebDavConfig(
                baseUrl = normalizeBaseUrl(baseUrl),
                username = username.trim(),
                remotePath = normalizeRemotePath(remotePath)
            ),
            password = password
        )
    }

    override suspend fun saveWebDavConfig(baseUrl: String, username: String, remotePath: String, password: String) {
        val encryptedPassword = cryptoManager.encryptForLocalStorage(password)
        settingRepository.updateWebDavConfig(
            WebDavConfig(
                baseUrl = normalizeBaseUrl(baseUrl),
                username = username.trim(),
                remotePath = normalizeRemotePath(remotePath),
                encryptedPassword = encryptedPassword.cipherText,
                passwordIv = encryptedPassword.iv
            )
        )
    }

    override suspend fun clearWebDavConfig() {
        settingRepository.updateWebDavConfig(null)
    }

    override suspend fun getWebDavConfig(): WebDavConfig? = settingRepository.fetchWebDavConfig()

    override suspend fun getWebDavPassword(): String? {
        val config = settingRepository.fetchWebDavConfig() ?: return null
        val encryptedPassword = config.encryptedPassword ?: return null
        val passwordIv = config.passwordIv ?: return null

        return cryptoManager.decryptFromLocalStorage(encryptedPassword, passwordIv)
    }

    override suspend fun listRemoteBackups(password: String): List<WebDavRemoteFile> {
        val config = requireWebDavConfig()
        return webDavRepository.listBackupFiles(config, password).sortedByDescending { it.modifiedAt ?: it.name }
    }

    override suspend fun detectUploadConflict(password: String): SyncConflict? {
        val config = requireWebDavConfig()
        val localBackupContent = exportBackupJson()
        val localBackup = parseBackup(localBackupContent)
        val remoteFile = webDavRepository.listBackupFiles(config, password)
            .sortedByDescending { it.modifiedAt ?: it.name }
            .firstOrNull() ?: return null
        val remoteContent = webDavRepository.downloadBackup(config, password, remoteFile.name)
        val remoteBackup = parseBackup(remoteContent)

        return if (remoteBackup.exportedAt > localBackup.exportedAt) {
            SyncConflict(
                localSummary = localBackup.summary,
                remoteSummary = remoteBackup.summary,
                localExportedAt = localBackup.exportedAt,
                remoteExportedAt = remoteBackup.exportedAt,
                remoteFileName = remoteFile.name
            )
        } else {
            null
        }
    }

    override suspend fun uploadBackup(password: String, overwrite: Boolean): String {
        val config = requireWebDavConfig()
        if (!overwrite) {
            detectUploadConflict(password)?.let { conflict ->
                throw IllegalStateException("Conflict detected with remote file ${conflict.remoteFileName}")
            }
        }

        val content = exportBackupJson()
        val fileName = buildBackupFileName()
        webDavRepository.uploadBackup(config, password, fileName, content)
        return fileName
    }

    override suspend fun downloadRemoteBackup(password: String, remoteFileName: String): String {
        val config = requireWebDavConfig()
        return webDavRepository.downloadBackup(config, password, remoteFileName)
    }

    override suspend fun deleteRemoteBackup(password: String, remotePath: String) {
        val config = requireWebDavConfig()
        webDavRepository.deleteBackup(config, password, remotePath)
    }

    private suspend fun requireWebDavConfig(): WebDavConfig {
        return settingRepository.fetchWebDavConfig()
            ?: throw IllegalStateException("WebDAV config is not set")
    }

    private fun buildBackupFileName(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US)
        return "gptmobile-backup-${formatter.format(Date())}.json"
    }

    private fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')
    private fun normalizeRemotePath(remotePath: String): String = remotePath.trim().trim('/')

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
