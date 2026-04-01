package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.sync.model.SyncErrorCategory
import dev.chungjungsoo.gptmobile.data.sync.model.SyncOperation
import dev.chungjungsoo.gptmobile.data.sync.model.SyncStatusSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal const val SYNC_STATUS_CARD_TAG = "sync_status_card"
internal const val SYNC_STATUS_LAST_EXPORT_TAG = "sync_status_last_export"
internal const val SYNC_STATUS_LAST_RESTORE_TAG = "sync_status_last_restore"
internal const val SYNC_STATUS_LAST_UPLOAD_TAG = "sync_status_last_upload"
internal const val SYNC_STATUS_LAST_DOWNLOAD_TAG = "sync_status_last_download"
internal const val SYNC_STATUS_LAST_CONNECTION_TEST_TAG = "sync_status_last_connection_test"
internal const val SYNC_STATUS_LAST_OPERATION_TAG = "sync_status_last_operation"
internal const val SYNC_STATUS_LAST_ERROR_TAG = "sync_status_last_error"
internal const val SYNC_STATUS_REMOTE_FILE_TAG = "sync_status_remote_file"

private val syncStatusTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
fun SyncStatusCard(snapshot: SyncStatusSnapshot?, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag(SYNC_STATUS_CARD_TAG)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!hasVisibleContent(snapshot)) {
                Text(
                    text = stringResource(R.string.sync_status_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            snapshot?.lastLocalExportAt?.let { recordedAt ->
                SyncStatusLine(
                    label = stringResource(R.string.sync_status_last_export),
                    value = formatTimestamp(recordedAt),
                    modifier = Modifier.testTag(SYNC_STATUS_LAST_EXPORT_TAG)
                )
            }
            snapshot?.lastLocalRestoreAt?.let { recordedAt ->
                SyncStatusLine(
                    label = stringResource(R.string.sync_status_last_restore),
                    value = formatTimestamp(recordedAt),
                    modifier = Modifier.testTag(SYNC_STATUS_LAST_RESTORE_TAG)
                )
            }
            snapshot?.lastCloudUploadAt?.let { recordedAt ->
                SyncStatusLine(
                    label = stringResource(R.string.sync_status_last_upload),
                    value = formatTimestamp(recordedAt),
                    modifier = Modifier.testTag(SYNC_STATUS_LAST_UPLOAD_TAG)
                )
            }
            snapshot?.lastCloudDownloadAt?.let { recordedAt ->
                SyncStatusLine(
                    label = stringResource(R.string.sync_status_last_download),
                    value = formatTimestamp(recordedAt),
                    modifier = Modifier.testTag(SYNC_STATUS_LAST_DOWNLOAD_TAG)
                )
            }
            snapshot?.let { status ->
                connectionTestValue(status)?.let { value ->
                    SyncStatusLine(
                        label = stringResource(R.string.sync_status_last_connection_test),
                        value = value,
                        modifier = Modifier.testTag(SYNC_STATUS_LAST_CONNECTION_TEST_TAG)
                    )
                }
            }
            snapshot?.let { status ->
                operationResultValue(status)?.let { value ->
                    SyncStatusLine(
                        label = stringResource(R.string.sync_status_last_operation),
                        value = value,
                        modifier = Modifier.testTag(SYNC_STATUS_LAST_OPERATION_TAG)
                    )
                }
            }
            snapshot?.lastErrorCategory?.let { category ->
                SyncStatusLine(
                    label = stringResource(R.string.sync_status_last_error),
                    value = stringResource(stableErrorMessageResId(category)),
                    modifier = Modifier.testTag(SYNC_STATUS_LAST_ERROR_TAG)
                )
            }
            snapshot?.lastRemoteFileName?.takeIf { it.isNotBlank() }?.let { remoteFileName ->
                SyncStatusLine(
                    label = stringResource(R.string.sync_status_last_remote_file_name),
                    value = remoteFileName,
                    modifier = Modifier.testTag(SYNC_STATUS_REMOTE_FILE_TAG)
                )
            }
        }
    }
}

@Composable
private fun SyncStatusLine(label: String, value: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.fillMaxWidth(),
        text = stringResource(R.string.sync_status_value_format, label, value),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun connectionTestValue(snapshot: SyncStatusSnapshot): String? {
    val recordedAt = snapshot.lastConnectionTestAt ?: return null
    val resultResId = if (snapshot.lastConnectionTestSuccess == true) {
        R.string.sync_status_result_success
    } else {
        R.string.sync_status_result_failed
    }
    return stringResource(
        R.string.sync_status_connection_result_format,
        stringResource(resultResId),
        formatTimestamp(recordedAt)
    )
}

@Composable
private fun operationResultValue(snapshot: SyncStatusSnapshot): String? {
    val operation = snapshot.lastOperation ?: return null
    val recordedAt = snapshot.lastOperationAt ?: return null
    val success = snapshot.lastOperationSuccess ?: return null
    val resultResId = if (success) {
        R.string.sync_status_result_success
    } else {
        R.string.sync_status_result_failed
    }
    return stringResource(
        R.string.sync_status_operation_result_format,
        stringResource(operationLabelResId(operation)),
        stringResource(resultResId),
        formatTimestamp(recordedAt)
    )
}

private fun hasVisibleContent(snapshot: SyncStatusSnapshot?): Boolean {
    if (snapshot == null) {
        return false
    }

    return snapshot.lastLocalExportAt != null ||
        snapshot.lastLocalRestoreAt != null ||
        snapshot.lastCloudUploadAt != null ||
        snapshot.lastCloudDownloadAt != null ||
        snapshot.lastConnectionTestAt != null ||
        snapshot.lastOperation != null ||
        snapshot.lastErrorCategory != null ||
        !snapshot.lastRemoteFileName.isNullOrBlank()
}

private fun formatTimestamp(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .format(syncStatusTimeFormatter)

private fun operationLabelResId(operation: SyncOperation): Int = when (operation) {
    SyncOperation.LOCAL_EXPORT -> R.string.sync_status_operation_local_export
    SyncOperation.LOCAL_RESTORE -> R.string.sync_status_operation_local_restore
    SyncOperation.CONNECTION_TEST -> R.string.sync_status_operation_connection_test
    SyncOperation.CLOUD_UPLOAD -> R.string.sync_status_operation_cloud_upload
    SyncOperation.CLOUD_DOWNLOAD -> R.string.sync_status_operation_cloud_download
    SyncOperation.CONFLICT_LOAD_REMOTE -> R.string.sync_status_operation_conflict_load_remote
}

private fun stableErrorMessageResId(category: SyncErrorCategory): Int = when (category) {
    SyncErrorCategory.BACKUP_PASSWORD_INVALID -> R.string.sync_error_backup_password_invalid
    SyncErrorCategory.BACKUP_FILE_INVALID -> R.string.sync_error_backup_file_invalid
    SyncErrorCategory.BACKUP_SCHEMA_UNSUPPORTED -> R.string.sync_error_backup_schema_unsupported
    SyncErrorCategory.WEBDAV_CONFIG_INVALID -> R.string.sync_error_webdav_config_invalid
    SyncErrorCategory.WEBDAV_AUTH_FAILED -> R.string.sync_error_webdav_auth_failed
    SyncErrorCategory.WEBDAV_NETWORK_ERROR -> R.string.sync_error_webdav_network_error
    SyncErrorCategory.WEBDAV_SERVER_ERROR -> R.string.sync_error_webdav_server_error
    SyncErrorCategory.UNKNOWN -> R.string.sync_error_unknown
}
