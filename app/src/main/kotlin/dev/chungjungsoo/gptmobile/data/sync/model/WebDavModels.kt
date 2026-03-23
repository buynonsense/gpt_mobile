package dev.chungjungsoo.gptmobile.data.sync.model

import kotlinx.serialization.Serializable

@Serializable
data class WebDavConfig(
    val baseUrl: String,
    val username: String,
    val remotePath: String,
    val encryptedPassword: String? = null,
    val passwordIv: String? = null
)

data class WebDavRemoteFile(
    val path: String,
    val name: String,
    val modifiedAt: String? = null,
    val contentLength: Long? = null,
    val etag: String? = null
)

data class SyncConflict(
    val localSummary: BackupSummary,
    val remoteSummary: BackupSummary,
    val localExportedAt: Long,
    val remoteExportedAt: Long,
    val remoteFileName: String
)
