package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.sync.SyncRepository
import dev.chungjungsoo.gptmobile.data.sync.model.BackupFile
import dev.chungjungsoo.gptmobile.data.sync.model.SyncConflict
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
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    data class UiState(
        val backupPassword: String = "",
        val restorePassword: String = "",
        val webDavPassword: String = "",
        val webDavBaseUrl: String = "",
        val webDavUsername: String = "",
        val webDavRemotePath: String = "",
        val selectedRemoteFile: String = "",
        val isBusy: Boolean = false,
        val statusMessage: String? = null,
        val errorMessage: String? = null,
        val localBackupJson: String? = null,
        val importedBackupJson: String? = null,
        val importedBackupSummary: BackupFile? = null,
        val remoteBackups: List<WebDavRemoteFile> = emptyList(),
        val uploadConflict: SyncConflict? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = syncRepository.getWebDavConfig()
            val password = syncRepository.getWebDavPassword()
            _uiState.update {
                it.copy(
                    webDavBaseUrl = config?.baseUrl.orEmpty(),
                    webDavUsername = config?.username.orEmpty(),
                    webDavRemotePath = config?.remotePath.orEmpty(),
                    webDavPassword = password.orEmpty()
                )
            }
        }
    }

    fun updateBackupPassword(value: String) = _uiState.update { it.copy(backupPassword = value) }

    fun updateRestorePassword(value: String) = _uiState.update { it.copy(restorePassword = value) }

    fun updateWebDavPassword(value: String) = _uiState.update { it.copy(webDavPassword = value) }

    fun updateWebDavBaseUrl(value: String) = _uiState.update { it.copy(webDavBaseUrl = value) }

    fun updateWebDavUsername(value: String) = _uiState.update { it.copy(webDavUsername = value) }

    fun updateWebDavRemotePath(value: String) = _uiState.update { it.copy(webDavRemotePath = value) }

    fun updateSelectedRemoteFile(value: String) = _uiState.update { it.copy(selectedRemoteFile = value) }

    fun updateImportedBackupJson(value: String) {
        _uiState.update { it.copy(importedBackupJson = value, importedBackupSummary = null) }
    }

    fun loadImportedSummary() {
        val content = _uiState.value.importedBackupJson.orEmpty()
        if (content.isBlank()) {
            showError(R.string.backup_content_required_for_import)
            return
        }

        launchSafely(action = {
            val summary = syncRepository.parseBackup(content)
            showStatus(R.string.backup_summary_parsed)
            _uiState.update { it.copy(importedBackupSummary = summary) }
        }, fallbackErrorResId = R.string.backup_import_failed)
    }

    fun exportBackup() {
        val password = _uiState.value.backupPassword
        if (password.isBlank()) {
            showError(R.string.backup_password_required)
            return
        }

        launchSafely(action = {
            val content = syncRepository.exportBackupJson(password)
            showStatus(R.string.backup_generated)
            _uiState.update {
                it.copy(
                    localBackupJson = content
                )
            }
        }, fallbackErrorResId = R.string.backup_export_failed)
    }

    fun restoreImportedBackup() {
        val content = _uiState.value.importedBackupJson.orEmpty()
        val password = _uiState.value.restorePassword
        if (content.isBlank()) {
            showError(R.string.backup_content_required_for_import)
            return
        }
        if (password.isBlank()) {
            showError(R.string.restore_password_required)
            return
        }

        launchSafely(action = {
            syncRepository.restoreBackupJson(content, password)
            showStatus(R.string.backup_restored)
        }, fallbackErrorResId = R.string.backup_restore_failed)
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
            showError(R.string.webdav_config_required)
            return
        }

        launchSafely(action = {
            syncRepository.testWebDavConnection(
                baseUrl = state.webDavBaseUrl,
                username = state.webDavUsername,
                remotePath = state.webDavRemotePath,
                password = state.webDavPassword
            )
            showStatus(R.string.webdav_connection_success)
        })
    }

    fun loadRemoteBackups() {
        val password = _uiState.value.webDavPassword
        if (password.isBlank()) {
            showError(R.string.webdav_password_required)
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
        if (state.backupPassword.isBlank()) {
            showError(R.string.backup_password_required)
            return
        }
        if (state.webDavPassword.isBlank()) {
            showError(R.string.webdav_password_required)
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
                password = state.backupPassword,
                overwrite = overwrite
            )
            val remoteFiles = syncRepository.listRemoteBackups(state.webDavPassword)
            showStatus(R.string.backup_uploaded, fileName)
            _uiState.update {
                it.copy(
                    uploadConflict = null,
                    remoteBackups = remoteFiles,
                    selectedRemoteFile = fileName
                )
            }
        })
    }

    fun downloadSelectedRemoteBackup() {
        val state = _uiState.value
        if (state.webDavPassword.isBlank()) {
            showError(R.string.webdav_password_required)
            return
        }
        if (state.selectedRemoteFile.isBlank()) {
            showError(R.string.remote_backup_required)
            return
        }

        launchSafely(action = {
            val content = syncRepository.downloadRemoteBackup(state.webDavPassword, state.selectedRemoteFile)
            val summary = syncRepository.parseBackup(content)
            showStatus(R.string.backup_downloaded)
            _uiState.update {
                it.copy(
                    importedBackupJson = content,
                    importedBackupSummary = summary
                )
            }
        })
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

    private fun launchSafely(action: suspend () -> Unit, fallbackErrorResId: Int = R.string.unknown_sync_error) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
            runCatching { action() }
                .onFailure { error ->
                    showError(error.message ?: appContext.getString(fallbackErrorResId))
                }
            _uiState.update { it.copy(isBusy = false) }
        }
    }
}
