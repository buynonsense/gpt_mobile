package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.StreamingStyle
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.sync.SyncErrorClassifier
import dev.chungjungsoo.gptmobile.data.sync.SyncRepository
import dev.chungjungsoo.gptmobile.data.sync.model.BackupFile
import dev.chungjungsoo.gptmobile.data.sync.model.SyncErrorCategory
import dev.chungjungsoo.gptmobile.data.sync.model.SyncConflict
import dev.chungjungsoo.gptmobile.data.sync.model.SyncOperation
import dev.chungjungsoo.gptmobile.data.sync.model.SyncStatusSnapshot
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavRemoteFile
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val settingRepository: SettingRepository = NoOpSettingRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    data class UiState(
        val webDavPassword: String = "",
        val webDavBaseUrl: String = "",
        val webDavUsername: String = "",
        val webDavRemotePath: String = "",
        val selectedTab: SyncPageTab = SyncPageTab.LOCAL,
        val showWebDavConfigDialog: Boolean = false,
        val selectedRemoteFile: String = "",
        val isBusy: Boolean = false,
        val statusMessage: String? = null,
        val errorMessage: String? = null,
        val generatedBackupJson: String? = null,
        val pendingExportSaveRequest: Boolean = false,
        val importedBackupJson: String? = null,
        val importedBackupFileName: String? = null,
        val importedBackupExportedAt: Long? = null,
        val remoteBackups: List<WebDavRemoteFile> = emptyList(),
        val uploadConflict: SyncConflict? = null,
        val syncStatusSnapshot: SyncStatusSnapshot? = null
    )

    enum class SyncPageTab {
        LOCAL,
        WEBDAV
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = syncRepository.getWebDavConfig()
            val password = syncRepository.getWebDavPassword()
            val snapshot = settingRepository.fetchSyncStatusSnapshot()
            _uiState.update {
                it.copy(
                    webDavBaseUrl = config?.baseUrl.orEmpty(),
                    webDavUsername = config?.username.orEmpty(),
                    webDavRemotePath = config?.remotePath.orEmpty(),
                    webDavPassword = password.orEmpty(),
                    syncStatusSnapshot = snapshot
                )
            }
        }
    }

    fun updateWebDavPassword(value: String) = _uiState.update { it.copy(webDavPassword = value) }

    fun updateWebDavBaseUrl(value: String) = _uiState.update { it.copy(webDavBaseUrl = value) }

    fun updateWebDavUsername(value: String) = _uiState.update { it.copy(webDavUsername = value) }

    fun updateWebDavRemotePath(value: String) = _uiState.update { it.copy(webDavRemotePath = value) }

    fun updateSelectedRemoteFile(value: String) = _uiState.update { it.copy(selectedRemoteFile = value) }

    fun selectTab(tab: SyncPageTab) = _uiState.update { it.copy(selectedTab = tab) }

    fun showWebDavConfigDialog() = _uiState.update { it.copy(showWebDavConfigDialog = true) }

    fun hideWebDavConfigDialog() = _uiState.update { it.copy(showWebDavConfigDialog = false) }

    fun updateImportedBackupJson(value: String) {
        _uiState.update {
            it.copy(
                importedBackupJson = value,
                importedBackupFileName = null,
                importedBackupExportedAt = null
            )
        }
    }

    fun exportBackup(requestSaveAfterExport: Boolean = false) {
        launchSafely(action = {
            val backupJson = syncRepository.exportBackupJson()
            recordOperationSuccess(SyncOperation.LOCAL_EXPORT)
            showStatus(R.string.backup_generated)
            _uiState.update {
                it.copy(
                    generatedBackupJson = backupJson,
                    pendingExportSaveRequest = requestSaveAfterExport
                )
            }
        }, operation = SyncOperation.LOCAL_EXPORT, fallbackErrorResId = R.string.backup_export_failed)
    }

    fun consumePendingExportSaveRequest() {
        _uiState.update { it.copy(pendingExportSaveRequest = false) }
    }

    fun restoreImportedBackup() {
        val content = _uiState.value.importedBackupJson.orEmpty()
        if (content.isBlank()) {
            showError(R.string.backup_content_required_for_import)
            return
        }

        launchSafely(action = {
            syncRepository.parseBackup(content)
            syncRepository.restoreBackupJson(content)
            recordOperationSuccess(SyncOperation.LOCAL_RESTORE)
            showStatus(R.string.backup_restored)
        }, fallbackErrorResId = R.string.backup_restore_failed)
    }

    fun importBackupFile(fileName: String, content: String) {
        launchSafely(action = {
            val backupFile = syncRepository.parseBackup(content)
            _uiState.update {
                it.copy(
                    importedBackupJson = content,
                    importedBackupFileName = fileName,
                    importedBackupExportedAt = backupFile.exportedAt
                )
            }
            showStatus(R.string.backup_summary_parsed)
        }, fallbackErrorResId = R.string.backup_import_failed)
    }

    fun saveWebDavConfig() {
        val state = _uiState.value
        if (state.webDavBaseUrl.isBlank() || state.webDavUsername.isBlank() || state.webDavPassword.isBlank()) {
            showError(R.string.webdav_config_required)
            return
        }

        launchSafely(action = {
            syncRepository.saveWebDavConfig(
                baseUrl = state.webDavBaseUrl,
                username = state.webDavUsername,
                remotePath = state.webDavRemotePath,
                password = state.webDavPassword
            )
            showStatus(R.string.webdav_config_saved)
        })
    }

    fun testWebDavConnection() {
        val state = _uiState.value
        if (state.webDavBaseUrl.isBlank() || state.webDavUsername.isBlank() || state.webDavPassword.isBlank()) {
            handleValidationFailure(
                operation = SyncOperation.CONNECTION_TEST,
                validationFailure = SyncErrorCategory.WEBDAV_CONFIG_INVALID
            )
            return
        }

        launchSafely(action = {
            syncRepository.testWebDavConnection(
                baseUrl = state.webDavBaseUrl,
                username = state.webDavUsername,
                remotePath = state.webDavRemotePath,
                password = state.webDavPassword
            )
            recordOperationSuccess(SyncOperation.CONNECTION_TEST)
            showStatus(R.string.webdav_connection_success)
        }, operation = SyncOperation.CONNECTION_TEST)
    }

    fun loadRemoteBackups() {
        val password = _uiState.value.webDavPassword
        if (password.isBlank()) {
            handleValidationFailure(
                operation = SyncOperation.CLOUD_DOWNLOAD,
                validationFailure = SyncErrorCategory.WEBDAV_CONFIG_INVALID
            )
            return
        }

        launchSafely(action = {
            val remoteFiles = syncRepository.listRemoteBackups(password)
            showStatus(R.string.remote_backups_refreshed)
            _uiState.update {
                it.copy(
                    remoteBackups = remoteFiles,
                    selectedRemoteFile = remoteFiles.firstOrNull()?.name.orEmpty()
                )
            }
        })
    }

    fun uploadBackup(overwrite: Boolean = false) {
        val state = _uiState.value
        if (state.webDavPassword.isBlank()) {
            handleValidationFailure(
                operation = SyncOperation.CLOUD_UPLOAD,
                validationFailure = SyncErrorCategory.WEBDAV_CONFIG_INVALID
            )
            return
        }

        launchSafely(action = {
            if (!overwrite) {
                val conflict = syncRepository.detectUploadConflict(state.webDavPassword)
                if (conflict != null) {
                    showStatus(R.string.upload_conflict_detected)
                    _uiState.update { it.copy(uploadConflict = conflict) }
                    return@launchSafely
                }
            }

            val fileName = syncRepository.uploadBackup(
                password = state.webDavPassword,
                overwrite = overwrite
            )
            val remoteFiles = syncRepository.listRemoteBackups(state.webDavPassword)
            recordOperationSuccess(
                operation = SyncOperation.CLOUD_UPLOAD,
                remoteFileName = fileName
            )
            showStatus(R.string.backup_uploaded, fileName)
            _uiState.update {
                it.copy(
                    uploadConflict = null,
                    remoteBackups = remoteFiles,
                    selectedRemoteFile = fileName
                )
            }
        }, operation = SyncOperation.CLOUD_UPLOAD)
    }

    fun downloadSelectedRemoteBackup() {
        val state = _uiState.value
        if (state.webDavPassword.isBlank()) {
            handleValidationFailure(
                operation = SyncOperation.CLOUD_DOWNLOAD,
                validationFailure = SyncErrorCategory.WEBDAV_CONFIG_INVALID
            )
            return
        }
        if (state.selectedRemoteFile.isBlank()) {
            showError(R.string.remote_backup_required)
            return
        }

        launchSafely(action = {
            val content = syncRepository.downloadRemoteBackup(state.webDavPassword, state.selectedRemoteFile)
            val backupFile = syncRepository.parseBackup(content)
            recordOperationSuccess(
                operation = SyncOperation.CLOUD_DOWNLOAD,
                remoteFileName = state.selectedRemoteFile
            )
            showStatus(R.string.backup_downloaded)
            _uiState.update {
                it.copy(
                    importedBackupJson = content,
                    importedBackupFileName = state.selectedRemoteFile,
                    importedBackupExportedAt = backupFile.exportedAt
                )
            }
        }, operation = SyncOperation.CLOUD_DOWNLOAD)
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    fun showStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message, errorMessage = null) }
    }

    fun showStatus(messageResId: Int, vararg formatArgs: Any) {
        showStatus(appContext.getString(messageResId, *formatArgs))
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message, statusMessage = null) }
    }

    fun showError(messageResId: Int, vararg formatArgs: Any) {
        showError(appContext.getString(messageResId, *formatArgs))
    }

    fun dismissConflict() {
        _uiState.update { it.copy(uploadConflict = null) }
    }

    fun resolveConflictByUsingRemoteBackup() {
        val conflict = _uiState.value.uploadConflict
        if (conflict == null) {
            showError(R.string.remote_backup_required)
            return
        }

        val password = _uiState.value.webDavPassword
        if (password.isBlank()) {
            handleValidationFailure(
                operation = SyncOperation.CONFLICT_LOAD_REMOTE,
                validationFailure = SyncErrorCategory.WEBDAV_CONFIG_INVALID
            )
            return
        }

        launchSafely(action = {
            val content = syncRepository.downloadRemoteBackup(password, conflict.remoteFileName)
            val backupFile = syncRepository.parseBackup(content)
            recordOperationSuccess(
                operation = SyncOperation.CONFLICT_LOAD_REMOTE,
                remoteFileName = conflict.remoteFileName
            )
            showStatus(R.string.conflict_remote_backup_loaded)
            _uiState.update {
                it.copy(
                    importedBackupJson = content,
                    importedBackupFileName = conflict.remoteFileName,
                    importedBackupExportedAt = backupFile.exportedAt,
                    selectedRemoteFile = conflict.remoteFileName,
                    uploadConflict = null
                )
            }
        }, operation = SyncOperation.CONFLICT_LOAD_REMOTE, fallbackErrorResId = R.string.backup_download_failed)
    }

    private fun launchSafely(
        action: suspend () -> Unit,
        operation: SyncOperation? = null,
        fallbackErrorResId: Int = R.string.unknown_sync_error
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
            runCatching { action() }
                .onFailure { error ->
                    if (operation == null) {
                        showError(nonSyncErrorMessage(error, fallbackErrorResId))
                    } else {
                        recordOperationFailure(operation, error)
                    }
                }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    private fun handleValidationFailure(operation: SyncOperation, validationFailure: SyncErrorCategory) {
        val category = SyncErrorClassifier.classifyValidationFailure(operation, validationFailure)
        val snapshot = buildFailureSnapshot(
            previous = _uiState.value.syncStatusSnapshot,
            operation = operation,
            category = category,
            recordedAt = System.currentTimeMillis()
        )
        showError(stableErrorMessage(category))
        _uiState.update { it.copy(syncStatusSnapshot = snapshot) }
        viewModelScope.launch {
            settingRepository.updateSyncStatusSnapshot(snapshot)
        }
    }

    private suspend fun recordOperationSuccess(operation: SyncOperation, remoteFileName: String? = null) {
        val snapshot = buildSuccessSnapshot(
            previous = _uiState.value.syncStatusSnapshot,
            operation = operation,
            recordedAt = System.currentTimeMillis(),
            remoteFileName = remoteFileName
        )
        persistSnapshot(snapshot)
    }

    private suspend fun recordOperationFailure(operation: SyncOperation, error: Throwable) {
        val category = classifySyncOperationError(operation, error)
        val snapshot = buildFailureSnapshot(
            previous = _uiState.value.syncStatusSnapshot,
            operation = operation,
            category = category,
            recordedAt = System.currentTimeMillis()
        )
        persistSnapshot(snapshot)
        showError(stableErrorMessage(category))
    }

    private suspend fun persistSnapshot(snapshot: SyncStatusSnapshot) {
        settingRepository.updateSyncStatusSnapshot(snapshot)
        _uiState.update { it.copy(syncStatusSnapshot = snapshot) }
    }

    private fun nonSyncErrorMessage(error: Throwable, fallbackErrorResId: Int): String {
        return error.message ?: appContext.getString(fallbackErrorResId)
    }

    private fun classifySyncOperationError(operation: SyncOperation, error: Throwable): SyncErrorCategory {
        if (operation in WEBDAV_CONFIG_REQUIRED_OPERATIONS && error.message == MISSING_WEBDAV_CONFIG_MESSAGE) {
            return SyncErrorCategory.WEBDAV_CONFIG_INVALID
        }
        return SyncErrorClassifier.classifyThrowable(error)
    }

    private fun buildSuccessSnapshot(
        previous: SyncStatusSnapshot?,
        operation: SyncOperation,
        recordedAt: Long,
        remoteFileName: String? = null
    ): SyncStatusSnapshot {
        val baseSnapshot = previous ?: SyncStatusSnapshot()
        return when (operation) {
            SyncOperation.LOCAL_EXPORT -> baseSnapshot.copy(
                lastLocalExportAt = recordedAt,
                lastOperation = operation,
                lastOperationAt = recordedAt,
                lastOperationSuccess = true,
                lastErrorCategory = null
            )

            SyncOperation.LOCAL_RESTORE -> baseSnapshot.copy(
                lastLocalRestoreAt = recordedAt,
                lastOperation = operation,
                lastOperationAt = recordedAt,
                lastOperationSuccess = true,
                lastErrorCategory = null
            )

            SyncOperation.CONNECTION_TEST -> baseSnapshot.copy(
                lastConnectionTestAt = recordedAt,
                lastConnectionTestSuccess = true,
                lastOperation = operation,
                lastOperationAt = recordedAt,
                lastOperationSuccess = true,
                lastErrorCategory = null
            )

            SyncOperation.CLOUD_UPLOAD -> baseSnapshot.copy(
                lastCloudUploadAt = recordedAt,
                lastOperation = operation,
                lastOperationAt = recordedAt,
                lastOperationSuccess = true,
                lastErrorCategory = null,
                lastRemoteFileName = remoteFileName ?: baseSnapshot.lastRemoteFileName
            )

            SyncOperation.CLOUD_DOWNLOAD,
            SyncOperation.CONFLICT_LOAD_REMOTE -> baseSnapshot.copy(
                lastCloudDownloadAt = recordedAt,
                lastOperation = operation,
                lastOperationAt = recordedAt,
                lastOperationSuccess = true,
                lastErrorCategory = null,
                lastRemoteFileName = remoteFileName ?: baseSnapshot.lastRemoteFileName
            )
        }
    }

    fun setImportedBackupMetadata(fileName: String, exportedAt: Long?) {
        _uiState.update {
            it.copy(
                importedBackupFileName = fileName,
                importedBackupExportedAt = exportedAt
            )
        }
    }

    private fun buildFailureSnapshot(
        previous: SyncStatusSnapshot?,
        operation: SyncOperation,
        category: SyncErrorCategory,
        recordedAt: Long
    ): SyncStatusSnapshot {
        val baseSnapshot = previous ?: SyncStatusSnapshot()
        return when (operation) {
            SyncOperation.CONNECTION_TEST -> baseSnapshot.copy(
                lastConnectionTestAt = recordedAt,
                lastConnectionTestSuccess = false,
                lastOperation = operation,
                lastOperationAt = recordedAt,
                lastOperationSuccess = false,
                lastErrorCategory = category
            )

            else -> baseSnapshot.copy(
                lastOperation = operation,
                lastOperationAt = recordedAt,
                lastOperationSuccess = false,
                lastErrorCategory = category
            )
        }
    }

    private fun stableErrorMessage(category: SyncErrorCategory): String {
        val messageResId = when (category) {
            SyncErrorCategory.BACKUP_PASSWORD_INVALID -> R.string.sync_error_backup_password_invalid
            SyncErrorCategory.BACKUP_FILE_INVALID -> R.string.sync_error_backup_file_invalid
            SyncErrorCategory.BACKUP_SCHEMA_UNSUPPORTED -> R.string.sync_error_backup_schema_unsupported
            SyncErrorCategory.WEBDAV_CONFIG_INVALID -> R.string.sync_error_webdav_config_invalid
            SyncErrorCategory.WEBDAV_AUTH_FAILED -> R.string.sync_error_webdav_auth_failed
            SyncErrorCategory.WEBDAV_NETWORK_ERROR -> R.string.sync_error_webdav_network_error
            SyncErrorCategory.WEBDAV_SERVER_ERROR -> R.string.sync_error_webdav_server_error
            SyncErrorCategory.UNKNOWN -> R.string.sync_error_unknown
        }
        return appContext.getString(messageResId)
    }

    private companion object {
        val WEBDAV_CONFIG_REQUIRED_OPERATIONS = setOf(
            SyncOperation.CLOUD_UPLOAD,
            SyncOperation.CLOUD_DOWNLOAD,
            SyncOperation.CONFLICT_LOAD_REMOTE
        )

        const val MISSING_WEBDAV_CONFIG_MESSAGE = "WebDAV config is not set"

        val NoOpSettingRepository = object : SettingRepository {
            override suspend fun fetchPlatforms(): List<Platform> = emptyList()

            override suspend fun fetchThemes(): ThemeSetting = ThemeSetting()

            override suspend fun fetchStreamingStyle(): StreamingStyle = StreamingStyle.TYPEWRITER

            override suspend fun fetchWebDavConfig() = null

            override suspend fun fetchSyncStatusSnapshot(): SyncStatusSnapshot? = null

            override suspend fun updatePlatforms(platforms: List<Platform>) = Unit

            override suspend fun updateThemes(themeSetting: ThemeSetting) = Unit

            override suspend fun updateStreamingStyle(style: StreamingStyle) = Unit

            override suspend fun updateWebDavConfig(config: dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig?) = Unit

            override suspend fun updateSyncStatusSnapshot(snapshot: SyncStatusSnapshot?) = Unit
        }
    }
}
