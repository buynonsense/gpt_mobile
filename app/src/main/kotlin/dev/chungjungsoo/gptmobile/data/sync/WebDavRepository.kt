package dev.chungjungsoo.gptmobile.data.sync

import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavRemoteFile

interface WebDavRepository {
    suspend fun testConnection(config: WebDavConfig, password: String)
    suspend fun listBackupFiles(config: WebDavConfig, password: String): List<WebDavRemoteFile>
    suspend fun uploadBackup(config: WebDavConfig, password: String, fileName: String, content: String)
    suspend fun downloadBackup(config: WebDavConfig, password: String, remotePath: String): String
    suspend fun deleteBackup(config: WebDavConfig, password: String, remotePath: String)
}
