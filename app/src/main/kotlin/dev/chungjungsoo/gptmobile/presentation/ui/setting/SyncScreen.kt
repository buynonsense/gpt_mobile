package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R

private const val SYNC_SCREEN_LOG_TAG = "SyncScreen"
internal const val SAVE_BACKUP_TO_FILE_BUTTON_TAG = "save_backup_to_file_button"
internal const val IMPORT_BACKUP_FROM_FILE_BUTTON_TAG = "import_backup_from_file_button"
internal const val SYNC_ERROR_MESSAGE_TAG = "sync_error_message"
internal const val SYNC_WEBDAV_SECTION_TITLE_TAG = "sync_webdav_section_title"
internal const val SYNC_TAB_LOCAL_TAG = "sync_tab_local"
internal const val SYNC_TAB_WEBDAV_TAG = "sync_tab_webdav"
internal const val OPEN_WEBDAV_CONFIG_DIALOG_TAG = "open_webdav_config_dialog"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onNavigationClick: () -> Unit,
    syncViewModel: SyncViewModel = hiltViewModel()
) {
    val uiState by syncViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveBackupLauncher = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri ->
        handleBackupFileCreated(
            uri = uri,
            context = context,
            syncViewModel = syncViewModel,
            backupContent = uiState.generatedBackupJson,
            backupSavedMessage = context.getString(R.string.backup_saved_to_file),
            backupSaveFailedMessage = context.getString(R.string.backup_save_failed)
        )
    }
    val importBackupLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        handleBackupFileSelected(
            uri = uri,
            context = context,
            syncViewModel = syncViewModel,
            backupImportFailedMessage = context.getString(R.string.backup_import_failed)
        )
    }

    LaunchedEffect(uiState.pendingExportSaveRequest, uiState.generatedBackupJson) {
        if (uiState.pendingExportSaveRequest && !uiState.generatedBackupJson.isNullOrBlank()) {
            syncViewModel.consumePendingExportSaveRequest()
            saveBackupLauncher.launch(context.getString(R.string.backup_file_name))
        }
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
            SyncTabs(
                selectedTab = uiState.selectedTab,
                onSelectTab = syncViewModel::selectTab
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp))

            when (uiState.selectedTab) {
                SyncViewModel.SyncPageTab.LOCAL -> LocalBackupContent(
                    uiState = uiState,
                    onExportBackup = { syncViewModel.exportBackup(requestSaveAfterExport = true) },
                    onImportFromFile = { importBackupLauncher.launch(arrayOf("application/json")) },
                    onRestoreBackup = syncViewModel::restoreImportedBackup
                )

                SyncViewModel.SyncPageTab.WEBDAV -> WebDavContent(
                    uiState = uiState,
                    onOpenConfigDialog = syncViewModel::showWebDavConfigDialog,
                    onTestConnection = syncViewModel::testWebDavConnection,
                    onRefreshRemoteBackups = syncViewModel::loadRemoteBackups,
                    onUpload = { syncViewModel.uploadBackup(overwrite = false) },
                    onDownload = syncViewModel::downloadSelectedRemoteBackup,
                    onSelectedRemoteFileChange = syncViewModel::updateSelectedRemoteFile
                )
            }

            uiState.statusMessage?.let { message ->
                SyncMessageCard(message = message)
            }
            uiState.errorMessage?.let { error ->
                SyncMessageCard(
                    message = error,
                    modifier = Modifier.testTag(SYNC_ERROR_MESSAGE_TAG)
                )
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

    if (uiState.showWebDavConfigDialog) {
        WebDavConfigDialog(
            uiState = uiState,
            onDismiss = syncViewModel::hideWebDavConfigDialog,
            onBaseUrlChange = syncViewModel::updateWebDavBaseUrl,
            onUsernameChange = syncViewModel::updateWebDavUsername,
            onPasswordChange = syncViewModel::updateWebDavPassword,
            onRemotePathChange = syncViewModel::updateWebDavRemotePath,
            onSave = {
                syncViewModel.saveWebDavConfig()
                syncViewModel.hideWebDavConfigDialog()
            }
        )
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

@Composable
private fun SyncTabs(
    selectedTab: SyncViewModel.SyncPageTab,
    onSelectTab: (SyncViewModel.SyncPageTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SyncTabCard(
            text = stringResource(R.string.sync_tab_local_backup),
            selected = selectedTab == SyncViewModel.SyncPageTab.LOCAL,
            modifier = Modifier
                .weight(1f)
                .testTag(SYNC_TAB_LOCAL_TAG),
            onClick = { onSelectTab(SyncViewModel.SyncPageTab.LOCAL) }
        )
        SyncTabCard(
            text = stringResource(R.string.sync_tab_webdav),
            selected = selectedTab == SyncViewModel.SyncPageTab.WEBDAV,
            modifier = Modifier
                .weight(1f)
                .testTag(SYNC_TAB_WEBDAV_TAG),
            onClick = { onSelectTab(SyncViewModel.SyncPageTab.WEBDAV) }
        )
    }
}

@Composable
private fun SyncTabCard(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedCard(modifier = modifier.clickable(onClick = onClick)) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            text = text,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun LocalBackupContent(
    uiState: SyncViewModel.UiState,
    onExportBackup: () -> Unit,
    onImportFromFile: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    SectionTitle(stringResource(R.string.local_backup))
    SectionDescription(stringResource(R.string.local_backup_description))
    SectionDescription(stringResource(R.string.backup_file_contains_api_key_warning))

    TextButton(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .testTag(SAVE_BACKUP_TO_FILE_BUTTON_TAG),
        enabled = !uiState.isBusy,
        onClick = onExportBackup
    ) {
        Text(stringResource(R.string.generate_backup_file))
    }
    TextButton(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .testTag(IMPORT_BACKUP_FROM_FILE_BUTTON_TAG),
        enabled = !uiState.isBusy,
        onClick = onImportFromFile
    ) {
        Text(stringResource(R.string.import_backup_from_file))
    }

    SectionTitle(stringResource(R.string.restore_backup))
    SectionDescription(stringResource(R.string.restore_backup_description))

    if (uiState.importedBackupFileName == null) {
        SyncMessageCard(message = stringResource(R.string.sync_status_empty))
    } else {
        SyncMessageCard(
            message = buildString {
                append(stringResource(R.string.backup_file_name_label))
                append("：")
                append(uiState.importedBackupFileName)
                uiState.importedBackupExportedAt?.let {
                    append("\n")
                    append(stringResource(R.string.backup_file_exported_at_label))
                    append("：")
                    append(formatTimestamp(it))
                }
            }
        )
        TextButton(
            modifier = Modifier.padding(horizontal = 20.dp),
            enabled = !uiState.isBusy,
            onClick = onRestoreBackup
        ) {
            Text(stringResource(R.string.restore_to_local))
        }
    }
}

@Composable
private fun WebDavContent(
    uiState: SyncViewModel.UiState,
    onOpenConfigDialog: () -> Unit,
    onTestConnection: () -> Unit,
    onRefreshRemoteBackups: () -> Unit,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    onSelectedRemoteFileChange: (String) -> Unit
) {
    SectionTitle(stringResource(R.string.sync_status_title))
    SectionDescription(stringResource(R.string.sync_status_description))
    SyncStatusCard(snapshot = uiState.syncStatusSnapshot)

    HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp))

    SectionTitle(
        text = stringResource(R.string.webdav_sync),
        modifier = Modifier.testTag(SYNC_WEBDAV_SECTION_TITLE_TAG)
    )
    SectionDescription(stringResource(R.string.webdav_sync_description))
    TextButton(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .testTag(OPEN_WEBDAV_CONFIG_DIALOG_TAG),
        enabled = !uiState.isBusy,
        onClick = onOpenConfigDialog
    ) {
        Text(stringResource(R.string.open_webdav_config_dialog))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onTestConnection, enabled = !uiState.isBusy) {
            Text(stringResource(R.string.test_connection))
        }
        TextButton(onClick = onRefreshRemoteBackups, enabled = !uiState.isBusy) {
            Text(stringResource(R.string.refresh_remote_backups))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onUpload, enabled = !uiState.isBusy) {
            Text(stringResource(R.string.upload_to_cloud))
        }
        TextButton(onClick = onDownload, enabled = !uiState.isBusy) {
            Text(stringResource(R.string.download_from_cloud))
        }
    }

    SectionDescription(stringResource(R.string.selected_remote_backup))
    uiState.remoteBackups.forEach { file ->
        SyncRemoteFileItem(
            fileName = file.name,
            selected = uiState.selectedRemoteFile == file.name,
            onClick = { onSelectedRemoteFileChange(file.name) }
        )
    }
    if (uiState.remoteBackups.isEmpty()) {
        SyncMessageCard(message = stringResource(R.string.no_remote_backups))
    }
}

@Composable
private fun SyncRemoteFileItem(fileName: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = fileName,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun WebDavConfigDialog(
    uiState: SyncViewModel.UiState,
    onDismiss: () -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRemotePathChange: (String) -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.save_webdav_config))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.webdav_config_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SimpleField(
                    value = uiState.webDavBaseUrl,
                    label = stringResource(R.string.webdav_base_url),
                    onValueChange = onBaseUrlChange
                )
                SimpleField(
                    value = uiState.webDavUsername,
                    label = stringResource(R.string.webdav_username),
                    onValueChange = onUsernameChange
                )
                PasswordField(
                    value = uiState.webDavPassword,
                    label = stringResource(R.string.webdav_password),
                    onValueChange = onPasswordChange
                )
                SimpleField(
                    value = uiState.webDavRemotePath,
                    label = stringResource(R.string.webdav_remote_path),
                    onValueChange = onRemotePathChange
                )
            }
        }
    )
}

@Composable
private fun SyncMessageCard(message: String, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = message,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun handleBackupFileSelected(
    uri: Uri?,
    context: Context,
    syncViewModel: SyncViewModel,
    backupImportFailedMessage: String
) {
    if (uri == null) {
        return
    }
    val content = readBackupFromUri(context, uri)
    if (content.isNullOrBlank()) {
        syncViewModel.showError(backupImportFailedMessage)
        return
    }

    syncViewModel.importBackupFile(
        fileName = uri.lastPathSegment?.substringAfterLast('/') ?: context.getString(R.string.backup_file_name),
        content = content
    )
}

private fun handleBackupFileCreated(
    uri: Uri?,
    context: Context,
    syncViewModel: SyncViewModel,
    backupContent: String?,
    backupSavedMessage: String,
    backupSaveFailedMessage: String
) {
    if (uri == null) {
        return
    }
    if (backupContent.isNullOrBlank()) {
        syncViewModel.showError(context.getString(R.string.backup_save_requires_content))
        return
    }
    if (writeBackupToUri(context, uri, backupContent)) {
        syncViewModel.showStatus(backupSavedMessage)
    } else {
        syncViewModel.showError(backupSaveFailedMessage)
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

private fun readBackupFromUri(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.readText()
        } ?: error("input stream unavailable")
    }.fold(
        onSuccess = { it },
        onFailure = { error ->
            Log.e(SYNC_SCREEN_LOG_TAG, "读取备份文件失败: uri=$uri", error)
            null
        }
    )

private fun formatTimestamp(timestamp: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    .format(java.util.Date(timestamp))

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) }
    )
}

@Composable
private fun SimpleField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) }
    )
}
