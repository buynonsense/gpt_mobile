package dev.chungjungsoo.gptmobile.data.sync

import dev.chungjungsoo.gptmobile.data.sync.model.SyncErrorCategory
import dev.chungjungsoo.gptmobile.data.sync.model.SyncOperation
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncErrorClassifierTest {
    @Test
    fun classify_invalidBackupPassword_returnsBackupPasswordInvalid() {
        val category = SyncErrorClassifier.classifyThrowable(
            SyncErrorClassifier.invalidBackupPasswordException()
        )

        assertEquals(SyncErrorCategory.BACKUP_PASSWORD_INVALID, category)
    }

    @Test
    fun classify_unsupportedSchema_returnsBackupSchemaUnsupported() {
        val category = SyncErrorClassifier.classifyThrowable(
            SyncErrorClassifier.unsupportedSchemaException(99)
        )

        assertEquals(SyncErrorCategory.BACKUP_SCHEMA_UNSUPPORTED, category)
    }

    @Test
    fun classify_backupSerializationFailure_returnsBackupFileInvalid() {
        val category = SyncErrorClassifier.classifyThrowable(SerializationException("bad json"))

        assertEquals(SyncErrorCategory.BACKUP_FILE_INVALID, category)
    }

    @Test
    fun classify_missingWebDavConfig_returnsWebDavConfigInvalid() {
        val category = SyncErrorClassifier.classifyValidationFailure(
            operation = SyncOperation.CONNECTION_TEST,
            validationFailure = SyncErrorCategory.WEBDAV_CONFIG_INVALID
        )

        assertEquals(SyncErrorCategory.WEBDAV_CONFIG_INVALID, category)
    }

    @Test
    fun classify_missingWebDavConfig_forLocalRestore_returnsUnknown() {
        val category = SyncErrorClassifier.classifyValidationFailure(
            operation = SyncOperation.LOCAL_RESTORE,
            validationFailure = SyncErrorCategory.WEBDAV_CONFIG_INVALID
        )

        assertEquals(SyncErrorCategory.UNKNOWN, category)
    }

    @Test
    fun classify_unknownFailure_returnsUnknown() {
        val category = SyncErrorClassifier.classifyThrowable(RuntimeException("boom"))

        assertEquals(SyncErrorCategory.UNKNOWN, category)
    }

    @Test
    fun classify_webDavAuthFailure_returnsAuthFailed() {
        val error = SyncOperationException(SyncErrorCategory.WEBDAV_AUTH_FAILED)

        assertEquals(
            SyncErrorCategory.WEBDAV_AUTH_FAILED,
            SyncErrorClassifier.classifyThrowable(error)
        )
    }

    @Test
    fun classify_webDavNetworkFailure_returnsNetworkError() {
        val error = SyncOperationException(SyncErrorCategory.WEBDAV_NETWORK_ERROR)

        assertEquals(
            SyncErrorCategory.WEBDAV_NETWORK_ERROR,
            SyncErrorClassifier.classifyThrowable(error)
        )
    }

    @Test
    fun classify_webDavServerFailure_returnsServerError() {
        val error = SyncOperationException(SyncErrorCategory.WEBDAV_SERVER_ERROR)

        assertEquals(
            SyncErrorCategory.WEBDAV_SERVER_ERROR,
            SyncErrorClassifier.classifyThrowable(error)
        )
    }
}
