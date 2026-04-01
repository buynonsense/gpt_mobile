package dev.chungjungsoo.gptmobile.data.sync.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncStatusSnapshot(
    val lastLocalExportAt: Long? = null,
    val lastLocalRestoreAt: Long? = null,
    val lastCloudUploadAt: Long? = null,
    val lastCloudDownloadAt: Long? = null,
    val lastConnectionTestAt: Long? = null,
    val lastConnectionTestSuccess: Boolean? = null,
    val lastOperation: SyncOperation? = null,
    val lastOperationAt: Long? = null,
    val lastOperationSuccess: Boolean? = null,
    val lastErrorCategory: SyncErrorCategory? = null,
    val lastRemoteFileName: String? = null
)

@Serializable
enum class SyncOperation {
    LOCAL_EXPORT,
    LOCAL_RESTORE,
    CONNECTION_TEST,
    CLOUD_UPLOAD,
    CLOUD_DOWNLOAD,
    CONFLICT_LOAD_REMOTE
}

@Serializable
enum class SyncErrorCategory {
    BACKUP_PASSWORD_INVALID,
    BACKUP_FILE_INVALID,
    BACKUP_SCHEMA_UNSUPPORTED,
    WEBDAV_CONFIG_INVALID,
    WEBDAV_AUTH_FAILED,
    WEBDAV_NETWORK_ERROR,
    WEBDAV_SERVER_ERROR,
    UNKNOWN
}
