package dev.chungjungsoo.gptmobile.data.sync

import dev.chungjungsoo.gptmobile.data.sync.model.SyncErrorCategory
import dev.chungjungsoo.gptmobile.data.sync.model.SyncOperation
import kotlinx.serialization.SerializationException

object SyncErrorClassifier {
    fun classifyThrowable(throwable: Throwable): SyncErrorCategory {
        return when (throwable) {
            is SyncOperationException -> throwable.category
            is SerializationException -> SyncErrorCategory.BACKUP_FILE_INVALID
            is IllegalArgumentException -> classifyIllegalArgumentException(throwable)
            else -> SyncErrorCategory.UNKNOWN
        }
    }

    fun classifyValidationFailure(
        operation: SyncOperation,
        validationFailure: SyncErrorCategory
    ): SyncErrorCategory {
        return when (validationFailure) {
            SyncErrorCategory.WEBDAV_CONFIG_INVALID -> {
                if (operation in webDavConfigOperations) {
                    SyncErrorCategory.WEBDAV_CONFIG_INVALID
                } else {
                    SyncErrorCategory.UNKNOWN
                }
            }

            else -> validationFailure
        }
    }

    internal fun invalidBackupPasswordException(): IllegalArgumentException {
        return IllegalArgumentException(INVALID_BACKUP_PASSWORD_MESSAGE)
    }

    internal fun unsupportedSchemaException(schemaVersion: Int): IllegalArgumentException {
        return IllegalArgumentException("$UNSUPPORTED_SCHEMA_MESSAGE_PREFIX: $schemaVersion")
    }

    private fun classifyIllegalArgumentException(throwable: IllegalArgumentException): SyncErrorCategory {
        val message = throwable.message.orEmpty()
        return when {
            message == INVALID_BACKUP_PASSWORD_MESSAGE -> SyncErrorCategory.BACKUP_PASSWORD_INVALID
            message.contains(UNSUPPORTED_SCHEMA_MESSAGE_PREFIX) -> SyncErrorCategory.BACKUP_SCHEMA_UNSUPPORTED
            else -> SyncErrorCategory.UNKNOWN
        }
    }

    private val webDavConfigOperations = setOf(
        SyncOperation.CONNECTION_TEST,
        SyncOperation.CLOUD_UPLOAD,
        SyncOperation.CLOUD_DOWNLOAD,
        SyncOperation.CONFLICT_LOAD_REMOTE
    )

    private const val INVALID_BACKUP_PASSWORD_MESSAGE = "Invalid backup password"
    private const val UNSUPPORTED_SCHEMA_MESSAGE_PREFIX = "Unsupported backup schema version"
}
