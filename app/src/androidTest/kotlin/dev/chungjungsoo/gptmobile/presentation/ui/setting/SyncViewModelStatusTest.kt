package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.StreamingStyle
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.sync.SyncOperationException
import dev.chungjungsoo.gptmobile.data.sync.SyncRepository
import dev.chungjungsoo.gptmobile.data.sync.model.BackupFile
import dev.chungjungsoo.gptmobile.data.sync.model.BackupPayload
import dev.chungjungsoo.gptmobile.data.sync.model.BackupDatabase
import dev.chungjungsoo.gptmobile.data.sync.model.BackupSettings
import dev.chungjungsoo.gptmobile.data.sync.model.BackupThemeSetting
import dev.chungjungsoo.gptmobile.data.sync.model.BackupStreamingStyle
import dev.chungjungsoo.gptmobile.data.sync.model.BackupSummary
import dev.chungjungsoo.gptmobile.data.sync.model.SyncConflict
import dev.chungjungsoo.gptmobile.data.sync.model.SyncErrorCategory
import dev.chungjungsoo.gptmobile.data.sync.model.SyncOperation
import dev.chungjungsoo.gptmobile.data.sync.model.SyncStatusSnapshot
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavRemoteFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncViewModelStatusTest {

    @Test
    fun init_loadsPersistedSyncStatusSnapshot() {
        val snapshot = SyncStatusSnapshot(
            lastLocalExportAt = 100L,
            lastOperation = SyncOperation.LOCAL_EXPORT,
            lastOperationAt = 100L,
            lastOperationSuccess = true
        )
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(),
            settingRepository = FakeSettingRepository(initialSnapshot = snapshot),
            appContext = ApplicationProvider.getApplicationContext()
        )

        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.syncStatusSnapshot != null
        }

        assertEquals(snapshot, viewModel.uiState.value.syncStatusSnapshot)
    }

    @Test
    fun exportBackup_success_persistsSnapshotAcrossViewModelRecreation() {
        val settingRepository = FakeSettingRepository()
        val syncRepository = FakeSyncRepository(exportedBackupJson = "{\"ok\":true}")
        val firstViewModel = SyncViewModel(
            syncRepository = syncRepository,
            settingRepository = settingRepository,
            appContext = ApplicationProvider.getApplicationContext()
        )

        firstViewModel.exportBackup()

        waitUntilState(timeoutMillis = 5_000) {
            settingRepository.lastSavedSnapshot?.lastOperation == SyncOperation.LOCAL_EXPORT
        }

        val secondViewModel = SyncViewModel(
            syncRepository = syncRepository,
            settingRepository = settingRepository,
            appContext = ApplicationProvider.getApplicationContext()
        )

        waitUntilState(timeoutMillis = 5_000) {
            secondViewModel.uiState.value.syncStatusSnapshot?.lastOperation == SyncOperation.LOCAL_EXPORT
        }

        assertEquals(SyncOperation.LOCAL_EXPORT, secondViewModel.uiState.value.syncStatusSnapshot?.lastOperation)
        assertEquals(true, secondViewModel.uiState.value.syncStatusSnapshot?.lastOperationSuccess)
    }

    @Test
    fun importBackupFile_setsRecoveryConfirmationMetadata() {
        val settingRepository = FakeSettingRepository()
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(),
            settingRepository = settingRepository,
            appContext = ApplicationProvider.getApplicationContext()
        )

        viewModel.importBackupFile(
            fileName = "backup.json",
            content = VALID_BACKUP_JSON
        )

        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.importedBackupFileName != null
        }

        assertEquals(
            "backup.json",
            viewModel.uiState.value.importedBackupFileName
        )
        assertEquals(1_234L, viewModel.uiState.value.importedBackupExportedAt)
    }

    @Test
    fun exportCurrentDataBeforeRestore_doesNotTriggerRestore() {
        val syncRepository = FakeSyncRepository(exportedBackupJson = CURRENT_BACKUP_JSON)
        val viewModel = SyncViewModel(
            syncRepository = syncRepository,
            settingRepository = FakeSettingRepository(),
            appContext = ApplicationProvider.getApplicationContext()
        )

        viewModel.importBackupFile(
            fileName = "backup.json",
            content = VALID_BACKUP_JSON
        )

        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.importedBackupFileName != null
        }

        viewModel.exportCurrentDataBeforeRestore()

        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.pendingExportSaveRequest
        }

        assertEquals(0, syncRepository.restoreCalls)
    }

    @Test
    fun downloadSelectedRemoteBackup_switchesToLocalTab() {
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(remoteBackupJson = VALID_BACKUP_JSON),
            settingRepository = FakeSettingRepository(),
            appContext = ApplicationProvider.getApplicationContext()
        )

        viewModel.selectTab(SyncViewModel.SyncPageTab.WEBDAV)
        viewModel.updateWebDavPassword("dav")
        viewModel.updateSelectedRemoteFile("remote.json")
        viewModel.downloadSelectedRemoteBackup()

        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.selectedTab == SyncViewModel.SyncPageTab.LOCAL
        }

        assertEquals(SyncViewModel.SyncPageTab.LOCAL, viewModel.uiState.value.selectedTab)
        assertEquals("remote.json", viewModel.uiState.value.importedBackupFileName)
        assertEquals(1_234L, viewModel.uiState.value.importedBackupExportedAt)
    }

    @Test
    fun restoreImportedBackup_callsRestoreOnlyOnConfirm() {
        val syncRepository = FakeSyncRepository()
        val viewModel = SyncViewModel(
            syncRepository = syncRepository,
            settingRepository = FakeSettingRepository(),
            appContext = ApplicationProvider.getApplicationContext()
        )

        viewModel.importBackupFile(
            fileName = "backup.json",
            content = VALID_BACKUP_JSON
        )

        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.importedBackupFileName != null
        }

        assertEquals(0, syncRepository.restoreCalls)

        viewModel.restoreImportedBackup()

        waitUntilState(timeoutMillis = 5_000) {
            syncRepository.restoreCalls == 1
        }

        assertEquals(1, syncRepository.restoreCalls)
        assertEquals(VALID_BACKUP_JSON, syncRepository.lastRestoredContent)
    }

    @Test
    fun uploadBackup_failure_usesStableErrorMessage() {
        val settingRepository = FakeSettingRepository()
        val syncRepository = FakeSyncRepository(
            uploadError = SyncOperationException(SyncErrorCategory.WEBDAV_AUTH_FAILED)
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = SyncViewModel(
            syncRepository = syncRepository,
            settingRepository = settingRepository,
            appContext = context
        )

        viewModel.updateWebDavPassword("dav")
        viewModel.uploadBackup()

        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.errorMessage != null
        }

        assertEquals(
            context.getString(R.string.sync_error_webdav_auth_failed),
            viewModel.uiState.value.errorMessage
        )
    }

    @Test
    fun restoreImportedBackup_failure_keepsOriginalErrorMessage() {
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(
                parseError = IllegalStateException("raw parse failure")
            ),
            settingRepository = FakeSettingRepository(),
            appContext = ApplicationProvider.getApplicationContext()
        )

        viewModel.updateImportedBackupJson("broken")
        viewModel.restoreImportedBackup()

        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.errorMessage != null
        }

        assertEquals("raw parse failure", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun restoreImportedBackup_withoutImportedFile_showsImportRequiredError() {
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(),
            settingRepository = FakeSettingRepository(),
            appContext = ApplicationProvider.getApplicationContext()
        )

        viewModel.restoreImportedBackup()

        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.errorMessage != null
        }

        assertEquals(
            ApplicationProvider.getApplicationContext<Context>().getString(R.string.backup_content_required_for_import),
            viewModel.uiState.value.errorMessage
        )
    }

    @Test
    fun uploadBackup_withoutWebDavPassword_recordsConfigError() {
        val settingRepository = FakeSettingRepository()
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(),
            settingRepository = settingRepository,
            appContext = ApplicationProvider.getApplicationContext()
        )

        viewModel.uploadBackup()

        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.syncStatusSnapshot?.lastErrorCategory != null
        }
        waitUntilSnapshotSaved(settingRepository) {
            it.lastErrorCategory != null
        }

        assertEquals(
            SyncErrorCategory.WEBDAV_CONFIG_INVALID,
            viewModel.uiState.value.syncStatusSnapshot?.lastErrorCategory
        )
        assertEquals(
            viewModel.uiState.value.syncStatusSnapshot,
            settingRepository.lastSavedSnapshot
        )
    }

    @Test
    fun uploadBackup_withoutSavedWebDavConfig_recordsConfigError() {
        val settingRepository = FakeSettingRepository()
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(savedWebDavConfig = false),
            settingRepository = settingRepository,
            appContext = ApplicationProvider.getApplicationContext()
        )

        populateWebDavForm(viewModel)
        viewModel.uploadBackup()

        waitUntilSnapshotSaved(settingRepository) {
            it.lastOperation == SyncOperation.CLOUD_UPLOAD
        }

        assertEquals(
            SyncErrorCategory.WEBDAV_CONFIG_INVALID,
            settingRepository.lastSavedSnapshot?.lastErrorCategory
        )
    }

    @Test
    fun downloadSelectedRemoteBackup_withoutSavedWebDavConfig_recordsConfigError() {
        val settingRepository = FakeSettingRepository()
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(savedWebDavConfig = false),
            settingRepository = settingRepository,
            appContext = ApplicationProvider.getApplicationContext()
        )

        populateWebDavForm(viewModel)
        viewModel.updateSelectedRemoteFile("remote.json")
        viewModel.downloadSelectedRemoteBackup()

        waitUntilSnapshotSaved(settingRepository) {
            it.lastOperation == SyncOperation.CLOUD_DOWNLOAD
        }

        assertEquals(
            SyncErrorCategory.WEBDAV_CONFIG_INVALID,
            settingRepository.lastSavedSnapshot?.lastErrorCategory
        )
    }

    @Test
    fun resolveConflictByUsingRemoteBackup_recordsConflictLoadRemoteSuccess() {
        val settingRepository = FakeSettingRepository()
        val syncRepository = FakeSyncRepository(
            conflict = fakeConflict(remoteFileName = "remote.json"),
            remoteBackupJson = VALID_BACKUP_JSON
        )
        val viewModel = SyncViewModel(
            syncRepository = syncRepository,
            settingRepository = settingRepository,
            appContext = ApplicationProvider.getApplicationContext()
        )

        viewModel.updateWebDavPassword("dav")
        viewModel.uploadBackup()
        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.uploadConflict != null
        }

        viewModel.resolveConflictByUsingRemoteBackup()

        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.syncStatusSnapshot?.lastOperation == SyncOperation.CONFLICT_LOAD_REMOTE
        }

        assertEquals(
            SyncOperation.CONFLICT_LOAD_REMOTE,
            viewModel.uiState.value.syncStatusSnapshot?.lastOperation
        )
        assertEquals(true, viewModel.uiState.value.syncStatusSnapshot?.lastOperationSuccess)
    }

    @Test
    fun resolveConflictByUsingRemoteBackup_withoutSavedWebDavConfig_recordsConfigError() {
        val settingRepository = FakeSettingRepository()
        val syncRepository = FakeSyncRepository(
            conflict = fakeConflict(remoteFileName = "remote.json"),
            savedWebDavConfig = false,
            allowConflictDetectionWithoutSavedConfig = true
        )
        val viewModel = SyncViewModel(
            syncRepository = syncRepository,
            settingRepository = settingRepository,
            appContext = ApplicationProvider.getApplicationContext()
        )

        populateWebDavForm(viewModel)
        viewModel.uploadBackup()
        waitUntilState(timeoutMillis = 5_000) {
            viewModel.uiState.value.uploadConflict != null
        }

        viewModel.resolveConflictByUsingRemoteBackup()

        waitUntilSnapshotSaved(settingRepository) {
            it.lastOperation == SyncOperation.CONFLICT_LOAD_REMOTE
        }

        assertEquals(
            SyncErrorCategory.WEBDAV_CONFIG_INVALID,
            settingRepository.lastSavedSnapshot?.lastErrorCategory
        )
    }

    private fun waitUntilState(timeoutMillis: Long, condition: () -> Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            instrumentation.waitForIdleSync()
            if (condition()) {
                return
            }
            Thread.sleep(50)
        }
        instrumentation.waitForIdleSync()
        assertTrue("等待状态超时", condition())
    }

    private fun waitUntilSnapshotSaved(
        settingRepository: FakeSettingRepository,
        condition: (SyncStatusSnapshot) -> Boolean
    ) {
        waitUntilState(timeoutMillis = 5_000) {
            settingRepository.lastSavedSnapshot?.let(condition) == true
        }
    }

    private fun populateWebDavForm(viewModel: SyncViewModel) {
        viewModel.updateWebDavBaseUrl("https://example.com")
        viewModel.updateWebDavUsername("user")
        viewModel.updateWebDavPassword("dav")
        viewModel.updateWebDavRemotePath("backup")
    }

    private class FakeSettingRepository(
        initialSnapshot: SyncStatusSnapshot? = null
    ) : SettingRepository {
        private var snapshot: SyncStatusSnapshot? = initialSnapshot

        var lastSavedSnapshot: SyncStatusSnapshot? = null
            private set

        override suspend fun fetchPlatforms(): List<Platform> {
            throw UnsupportedOperationException("fetchPlatforms is not used in this test")
        }

        override suspend fun fetchThemes(): ThemeSetting {
            throw UnsupportedOperationException("fetchThemes is not used in this test")
        }

        override suspend fun fetchStreamingStyle(): StreamingStyle {
            throw UnsupportedOperationException("fetchStreamingStyle is not used in this test")
        }

        override suspend fun fetchWebDavConfig(): WebDavConfig? = null

        override suspend fun fetchSyncStatusSnapshot(): SyncStatusSnapshot? = snapshot

        override suspend fun updatePlatforms(platforms: List<Platform>) {
            throw UnsupportedOperationException("updatePlatforms is not used in this test")
        }

        override suspend fun updateThemes(themeSetting: ThemeSetting) {
            throw UnsupportedOperationException("updateThemes is not used in this test")
        }

        override suspend fun updateStreamingStyle(style: StreamingStyle) {
            throw UnsupportedOperationException("updateStreamingStyle is not used in this test")
        }

        override suspend fun updateWebDavConfig(config: WebDavConfig?) {
            throw UnsupportedOperationException("updateWebDavConfig is not used in this test")
        }

        override suspend fun updateSyncStatusSnapshot(snapshot: SyncStatusSnapshot?) {
            this.snapshot = snapshot
            lastSavedSnapshot = snapshot
        }
    }

    private class FakeSyncRepository(
        private val exportedBackupJson: String = "",
        private val parseError: Throwable? = null,
        private val uploadError: Throwable? = null,
        private val conflict: SyncConflict? = null,
        private val remoteBackupJson: String = "",
        private val uploadFileName: String = "remote.json",
        private val savedWebDavConfig: Boolean = true,
        private val allowConflictDetectionWithoutSavedConfig: Boolean = false
    ) : SyncRepository {

        var restoreCalls: Int = 0
            private set

        var lastRestoredContent: String? = null
            private set

        override suspend fun exportBackupJson(): String = exportedBackupJson

        override suspend fun restoreBackupJson(content: String) {
            restoreCalls += 1
            lastRestoredContent = content
        }

        override suspend fun parseBackup(content: String): BackupFile {
            parseError?.let { throw it }
            return BackupFile(
                schemaVersion = 1,
                exportedAt = 1_234L,
                appVersion = "test",
                backupType = "local",
                summary = BackupSummary(
                    chatRoomCount = 0,
                    messageCount = 0,
                    aiMaskCount = 0,
                    containsSecrets = false
                ),
                payload = BackupPayload(
                    settings = BackupSettings(
                        platforms = emptyList(),
                        theme = BackupThemeSetting(
                            dynamicTheme = "OFF",
                            themeMode = "SYSTEM"
                        ),
                        streamingStyle = BackupStreamingStyle(
                            value = 0,
                            name = "TYPEWRITER"
                        )
                    ),
                    database = BackupDatabase(
                        chatRooms = emptyList(),
                        messages = emptyList(),
                        aiMasks = emptyList()
                    )
                )
            )
        }

        override suspend fun testWebDavConnection(baseUrl: String, username: String, remotePath: String, password: String) = Unit

        override suspend fun saveWebDavConfig(baseUrl: String, username: String, remotePath: String, password: String) = Unit

        override suspend fun clearWebDavConfig() = Unit

        override suspend fun getWebDavConfig(): WebDavConfig? = null

        override suspend fun getWebDavPassword(): String? = null

        override suspend fun listRemoteBackups(password: String): List<WebDavRemoteFile> {
            requireSavedWebDavConfig()
            return listOf(
                WebDavRemoteFile(path = "/$uploadFileName", name = uploadFileName)
            )
        }

        override suspend fun detectUploadConflict(password: String): SyncConflict? {
            if (!savedWebDavConfig && !allowConflictDetectionWithoutSavedConfig) {
                requireSavedWebDavConfig()
            }
            return conflict
        }

        override suspend fun uploadBackup(password: String, overwrite: Boolean): String {
            requireSavedWebDavConfig()
            uploadError?.let { throw it }
            return uploadFileName
        }

        override suspend fun downloadRemoteBackup(password: String, remoteFileName: String): String {
            requireSavedWebDavConfig()
            return remoteBackupJson
        }

        override suspend fun deleteRemoteBackup(password: String, remotePath: String) = Unit

        private fun requireSavedWebDavConfig() {
            check(savedWebDavConfig) { "WebDAV config is not set" }
        }
    }

    companion object {
        private const val CURRENT_BACKUP_JSON = "{\"current\":true}"
        private const val VALID_BACKUP_JSON = "{\"remote\":true}"

        private fun fakeConflict(remoteFileName: String): SyncConflict = SyncConflict(
            localSummary = BackupSummary(
                chatRoomCount = 1,
                messageCount = 2,
                aiMaskCount = 3,
                containsSecrets = true
            ),
            remoteSummary = BackupSummary(
                chatRoomCount = 4,
                messageCount = 5,
                aiMaskCount = 6,
                containsSecrets = true
            ),
            localExportedAt = 10L,
            remoteExportedAt = 20L,
            remoteFileName = remoteFileName
        )
    }
}
