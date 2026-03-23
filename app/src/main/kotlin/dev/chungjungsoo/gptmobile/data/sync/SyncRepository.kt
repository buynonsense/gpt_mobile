package dev.chungjungsoo.gptmobile.data.sync

import dev.chungjungsoo.gptmobile.data.sync.model.BackupFile
import dev.chungjungsoo.gptmobile.data.sync.model.SyncConflict
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavRemoteFile

interface SyncRepository {
    suspend fun exportBackupJson(password: String): String
    suspend fun restoreBackupJson(content: String, password: String)
    suspend fun parseBackup(content: String): BackupFile
    suspend fun testWebDavConnection(baseUrl: String, username: String, remotePath: String, password: String)
    suspend fun saveWebDavConfig(baseUrl: String, username: String, remotePath: String, password: String)
    suspend fun clearWebDavConfig()
    suspend fun getWebDavConfig(): WebDavConfig?
    suspend fun getWebDavPassword(): String?
    suspend fun listRemoteBackups(password: String): List<WebDavRemoteFile>
    suspend fun detectUploadConflict(password: String): SyncConflict?
    suspend fun uploadBackup(password: String, overwrite: Boolean): String
    suspend fun downloadRemoteBackup(password: String, remoteFileName: String): String
}
