package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val syncRepository: SyncRepository
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
            _uiState.update { it.copy(errorMessage = "请先粘贴备份内容") }
            return
        }

        launchSafely(action = {
            val summary = syncRepository.parseBackup(content)
            _uiState.update { it.copy(importedBackupSummary = summary, statusMessage = "已解析备份摘要") }
        })
    }

    fun exportBackup() {
        val password = _uiState.value.backupPassword
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入备份密码") }
            return
        }

        launchSafely(action = {
            val content = syncRepository.exportBackupJson(password)
            _uiState.update {
                it.copy(
                    localBackupJson = content,
                    statusMessage = "备份已生成，可复制或保存到文件"
                )
            }
        })
    }

    fun restoreImportedBackup() {
        val content = _uiState.value.importedBackupJson.orEmpty()
        val password = _uiState.value.restorePassword
        if (content.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请先提供备份内容") }
            return
        }
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入恢复密码") }
            return
        }

        launchSafely(action = {
            syncRepository.restoreBackupJson(content, password)
            _uiState.update { it.copy(statusMessage = "本地数据已覆盖恢复") }
        })
    }

    fun saveWebDavConfig() {
        val state = _uiState.value
        if (state.webDavBaseUrl.isBlank() || state.webDavUsername.isBlank() || state.webDavPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请填写完整的 WebDAV 地址、用户名和密码") }
            return
        }

        launchSafely(action = {
            syncRepository.saveWebDavConfig(
                baseUrl = state.webDavBaseUrl,
                username = state.webDavUsername,
                remotePath = state.webDavRemotePath,
                password = state.webDavPassword
            )
            _uiState.update { it.copy(statusMessage = "WebDAV 配置已保存") }
        })
    }

    fun testWebDavConnection() {
        val state = _uiState.value
        if (state.webDavBaseUrl.isBlank() || state.webDavUsername.isBlank() || state.webDavPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请填写完整的 WebDAV 地址、用户名和密码") }
            return
        }

        launchSafely(action = {
            syncRepository.testWebDavConnection(
                baseUrl = state.webDavBaseUrl,
                username = state.webDavUsername,
                remotePath = state.webDavRemotePath,
                password = state.webDavPassword
            )
            _uiState.update { it.copy(statusMessage = "WebDAV 连接成功") }
        })
    }

    fun loadRemoteBackups() {
        val password = _uiState.value.webDavPassword
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入 WebDAV 密码") }
            return
        }

        launchSafely(action = {
            val remoteFiles = syncRepository.listRemoteBackups(password)
            _uiState.update {
                it.copy(
                    remoteBackups = remoteFiles,
                    statusMessage = "已刷新云端备份列表",
                    selectedRemoteFile = remoteFiles.firstOrNull()?.name.orEmpty()
                )
            }
        })
    }

    fun uploadBackup(overwrite: Boolean = false) {
        val state = _uiState.value
        if (state.backupPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入备份密码") }
            return
        }
        if (state.webDavPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入 WebDAV 密码") }
            return
        }

        launchSafely(action = {
            if (!overwrite) {
                val conflict = syncRepository.detectUploadConflict(state.webDavPassword)
                if (conflict != null) {
                    _uiState.update { it.copy(uploadConflict = conflict, statusMessage = "检测到云端冲突，请手动选择") }
                    return@launchSafely
                }
            }

            val fileName = syncRepository.uploadBackup(
                password = state.backupPassword,
                overwrite = overwrite
            )
            val remoteFiles = syncRepository.listRemoteBackups(state.webDavPassword)
            _uiState.update {
                it.copy(
                    uploadConflict = null,
                    remoteBackups = remoteFiles,
                    selectedRemoteFile = fileName,
                    statusMessage = "已上传到云端：$fileName"
                )
            }
        })
    }

    fun downloadSelectedRemoteBackup() {
        val state = _uiState.value
        if (state.webDavPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入 WebDAV 密码") }
            return
        }
        if (state.selectedRemoteFile.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请先选择云端备份文件") }
            return
        }

        launchSafely(action = {
            val content = syncRepository.downloadRemoteBackup(state.webDavPassword, state.selectedRemoteFile)
            val summary = syncRepository.parseBackup(content)
            _uiState.update {
                it.copy(
                    importedBackupJson = content,
                    importedBackupSummary = summary,
                    statusMessage = "已下载云端备份，可选择恢复到本地"
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

    fun dismissConflict() {
        _uiState.update { it.copy(uploadConflict = null) }
    }

    private fun launchSafely(action: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
            runCatching { action() }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "发生未知错误") }
                }
            _uiState.update { it.copy(isBusy = false) }
        }
    }
}
