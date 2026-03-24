package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton

private const val SYNC_SCREEN_LOG_TAG = "SyncScreen"
internal const val SAVE_BACKUP_TO_FILE_BUTTON_TAG = "save_backup_to_file_button"
internal const val IMPORT_BACKUP_FROM_FILE_BUTTON_TAG = "import_backup_from_file_button"
internal const val SYNC_ERROR_MESSAGE_TAG = "sync_error_message"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onNavigationClick: () -> Unit,
    syncViewModel: SyncViewModel = hiltViewModel()
) {
    val uiState by syncViewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val backupCopiedMessage = stringResource(R.string.backup_copied)
    val saveBackupToFileMessage = stringResource(R.string.save_backup_to_file)
    val backupFileName = stringResource(R.string.backup_file_name)
    val backupSaveRequiresContentMessage = stringResource(R.string.backup_save_requires_content)
    val backupSavedMessage = stringResource(R.string.backup_saved_to_file)
    val backupSaveFailedMessage = stringResource(R.string.backup_save_failed)
    val importBackupFromFileMessage = stringResource(R.string.import_backup_from_file)
    val backupImportFailedMessage = stringResource(R.string.backup_import_failed)
    val saveBackupLauncher = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri ->
        handleBackupFileCreated(
            uri = uri,
            backupContent = uiState.localBackupJson,
            context = context,
            syncViewModel = syncViewModel,
            backupSaveRequiresContentMessage = backupSaveRequiresContentMessage,
            backupSavedMessage = backupSavedMessage,
            backupSaveFailedMessage = backupSaveFailedMessage
        )
    }
    val importBackupLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        handleBackupFileSelected(
            uri = uri,
            context = context,
            syncViewModel = syncViewModel,
            backupImportFailedMessage = backupImportFailedMessage
        )
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Text(
                        text = stringResource(R.string.data_sync),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            SectionTitle(stringResource(R.string.local_backup))
            SectionDescription(stringResource(R.string.local_backup_description))
            PasswordField(
                value = uiState.backupPassword,
                label = stringResource(R.string.backup_password),
                onValueChange = syncViewModel::updateBackupPassword
            )
            Text(
                modifier = Modifier.padding(horizontal = 20.dp),
                text = stringResource(R.string.backup_password_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrimaryLongButton(
                text = stringResource(R.string.generate_backup),
                enabled = !uiState.isBusy,
                onClick = syncViewModel::exportBackup
            )
            TextButton(
                modifier = Modifier.padding(horizontal = 20.dp),
                enabled = !uiState.localBackupJson.isNullOrBlank(),
                onClick = {
                    clipboardManager.setText(AnnotatedString(uiState.localBackupJson.orEmpty()))
                    syncViewModel.showStatus(backupCopiedMessage)
                }
            ) {
                Text(stringResource(R.string.copy_backup_content))
            }
            TextButton(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .testTag(SAVE_BACKUP_TO_FILE_BUTTON_TAG),
                enabled = !uiState.isBusy,
                onClick = {
                    launchBackupSave(
                        backupContent = uiState.localBackupJson,
                        backupFileName = backupFileName,
                        backupSaveRequiresContentMessage = backupSaveRequiresContentMessage,
                        syncViewModel = syncViewModel,
                        launchCreateDocument = saveBackupLauncher::launch
                    )
                }
            ) {
                Text(saveBackupToFileMessage)
            }
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                value = uiState.localBackupJson.orEmpty(),
                onValueChange = {},
                minLines = 6,
                readOnly = true,
                label = { Text(stringResource(R.string.generated_backup_content)) }
            )
            Text(
                modifier = Modifier.padding(horizontal = 20.dp),
                text = stringResource(R.string.generated_backup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp))

            SectionTitle(stringResource(R.string.restore_backup))
            SectionDescription(stringResource(R.string.restore_backup_description))
            TextButton(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .testTag(IMPORT_BACKUP_FROM_FILE_BUTTON_TAG),
                enabled = !uiState.isBusy,
                onClick = { importBackupLauncher.launch(arrayOf("application/json")) }
            ) {
                Text(importBackupFromFileMessage)
            }
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                value = uiState.importedBackupJson.orEmpty(),
                onValueChange = syncViewModel::updateImportedBackupJson,
                minLines = 6,
                label = { Text(stringResource(R.string.backup_content)) }
            )
            PasswordField(
                value = uiState.restorePassword,
                label = stringResource(R.string.restore_password),
                onValueChange = syncViewModel::updateRestorePassword
            )
            Text(
                modifier = Modifier.padding(horizontal = 20.dp),
                text = stringResource(R.string.restore_password_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = syncViewModel::loadImportedSummary, enabled = !uiState.isBusy) {
                    Text(stringResource(R.string.parse_backup_summary))
                }
                TextButton(onClick = syncViewModel::restoreImportedBackup, enabled = !uiState.isBusy) {
                    Text(stringResource(R.string.restore_to_local))
                }
            }
            uiState.importedBackupSummary?.let { backup ->
                BackupSummaryCard(
                    summaryText = stringResource(
                        R.string.backup_summary_format,
                        backup.summary.chatRoomCount,
                        backup.summary.messageCount,
                        backup.summary.aiMaskCount,
                        if (backup.summary.containsSecrets) stringResource(R.string.contains_api_keys) else stringResource(R.string.no_api_keys)
                    )
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp))

            SectionTitle(stringResource(R.string.webdav_sync))
            SectionDescription(stringResource(R.string.webdav_sync_description))
            SimpleField(
                value = uiState.webDavBaseUrl,
                label = stringResource(R.string.webdav_base_url),
                onValueChange = syncViewModel::updateWebDavBaseUrl
            )
            SimpleField(
                value = uiState.webDavUsername,
                label = stringResource(R.string.webdav_username),
                onValueChange = syncViewModel::updateWebDavUsername
            )
            PasswordField(
                value = uiState.webDavPassword,
                label = stringResource(R.string.webdav_password),
                onValueChange = syncViewModel::updateWebDavPassword
            )
            SimpleField(
                value = uiState.webDavRemotePath,
                label = stringResource(R.string.webdav_remote_path),
                onValueChange = syncViewModel::updateWebDavRemotePath
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = syncViewModel::saveWebDavConfig, enabled = !uiState.isBusy) {
                    Text(stringResource(R.string.save_webdav_config))
                }
                TextButton(onClick = syncViewModel::testWebDavConnection, enabled = !uiState.isBusy) {
                    Text(stringResource(R.string.test_connection))
                }
                TextButton(onClick = syncViewModel::loadRemoteBackups, enabled = !uiState.isBusy) {
                    Text(stringResource(R.string.refresh_remote_backups))
                }
            }
            SimpleField(
                value = uiState.selectedRemoteFile,
                label = stringResource(R.string.selected_remote_backup),
                onValueChange = syncViewModel::updateSelectedRemoteFile
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = { syncViewModel.uploadBackup(overwrite = false) }, enabled = !uiState.isBusy) {
                    Text(stringResource(R.string.upload_to_cloud))
                }
                TextButton(onClick = syncViewModel::downloadSelectedRemoteBackup, enabled = !uiState.isBusy) {
                    Text(stringResource(R.string.download_from_cloud))
                }
            }
            BackupSummaryCard(
                summaryText = if (uiState.remoteBackups.isEmpty()) {
                    stringResource(R.string.no_remote_backups)
                } else {
                    uiState.remoteBackups.joinToString(separator = "\n") { file ->
                        val segments = buildList {
                            add(file.name)
                            file.modifiedAt?.let { add(it) }
                            file.contentLength?.let {
                                add(context.getString(R.string.remote_backup_size_bytes, it))
                            }
                        }
                        segments.reduce { acc, segment ->
                            context.getString(R.string.remote_backup_list_item, acc, segment)
                        }
                    }
                }
            )

            uiState.statusMessage?.let { message ->
                BackupSummaryCard(summaryText = message)
            }
            uiState.errorMessage?.let { error ->
                BackupSummaryCard(
                    summaryText = error,
                    modifier = Modifier.testTag(SYNC_ERROR_MESSAGE_TAG)
                )
            }
            TextButton(
                modifier = Modifier.padding(horizontal = 20.dp),
                onClick = syncViewModel::clearMessages,
                enabled = uiState.statusMessage != null || uiState.errorMessage != null
            ) {
                Text(stringResource(R.string.clear_status_message))
            }

            if (uiState.isBusy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    uiState.uploadConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = syncViewModel::dismissConflict,
            confirmButton = {
                TextButton(onClick = { syncViewModel.uploadBackup(overwrite = true) }) {
                    Text(stringResource(R.string.overwrite_remote_backup))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = syncViewModel::resolveConflictByUsingRemoteBackup) {
                        Text(stringResource(R.string.use_remote_backup))
                    }
                    TextButton(onClick = syncViewModel::dismissConflict) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
            title = { Text(stringResource(R.string.sync_conflict_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.sync_conflict_message,
                        conflict.remoteFileName,
                        conflict.localSummary.chatRoomCount,
                        conflict.remoteSummary.chatRoomCount
                    )
                )
            }
        )
    }
}

private fun handleBackupFileSelected(
    uri: Uri?,
    context: Context,
    syncViewModel: SyncViewModel,
    backupImportFailedMessage: String
) {
    val content = readBackupFromUri(context, uri)
    if (content.isNullOrBlank()) {
        syncViewModel.showError(backupImportFailedMessage)
        return
    }

    syncViewModel.updateImportedBackupJson(content)
    syncViewModel.loadImportedSummary()
}

private fun launchBackupSave(
    backupContent: String?,
    backupFileName: String,
    backupSaveRequiresContentMessage: String,
    syncViewModel: SyncViewModel,
    launchCreateDocument: (String) -> Unit
) {
    if (backupContent.isNullOrBlank()) {
        syncViewModel.showError(backupSaveRequiresContentMessage)
        return
    }

    launchCreateDocument(backupFileName)
}

private fun handleBackupFileCreated(
    uri: Uri?,
    backupContent: String?,
    context: Context,
    syncViewModel: SyncViewModel,
    backupSaveRequiresContentMessage: String,
    backupSavedMessage: String,
    backupSaveFailedMessage: String
) {
    when {
        uri == null -> Unit
        backupContent.isNullOrBlank() -> syncViewModel.showError(backupSaveRequiresContentMessage)
        writeBackupToUri(context, uri, backupContent) -> syncViewModel.showStatus(backupSavedMessage)
        else -> syncViewModel.showError(backupSaveFailedMessage)
    }
}

private fun writeBackupToUri(context: Context, uri: Uri, backupContent: String): Boolean =
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(backupContent.toByteArray())
        } ?: error("output stream unavailable")
    }.fold(
        onSuccess = { true },
        onFailure = { error ->
            Log.e(SYNC_SCREEN_LOG_TAG, "保存备份文件失败: uri=$uri", error)
            false
        }
    )

private fun readBackupFromUri(context: Context, uri: Uri?): String? =
    runCatching {
        val targetUri = uri ?: error("uri unavailable")
        context.contentResolver.openInputStream(targetUri)?.bufferedReader()?.use { reader ->
            reader.readText()
        } ?: error("input stream unavailable")
    }.fold(
        onSuccess = { it },
        onFailure = { error ->
            Log.e(SYNC_SCREEN_LOG_TAG, "读取备份文件失败: uri=$uri", error)
            null
        }
    )

@Composable
private fun SectionTitle(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        text = text,
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun SectionDescription(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 20.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PasswordField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation()
    )
}

@Composable
private fun SimpleField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) }
    )
}

@Composable
private fun BackupSummaryCard(summaryText: String, modifier: Modifier = Modifier) {
    SelectionContainer {
        Text(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            text = summaryText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
