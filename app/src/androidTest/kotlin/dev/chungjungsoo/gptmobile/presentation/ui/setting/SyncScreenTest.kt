package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.StreamingStyle
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.sync.SyncRepository
import dev.chungjungsoo.gptmobile.data.sync.model.BackupFile
import dev.chungjungsoo.gptmobile.data.sync.model.BackupSummary
import dev.chungjungsoo.gptmobile.data.sync.model.SyncConflict
import dev.chungjungsoo.gptmobile.data.sync.model.SyncErrorCategory
import dev.chungjungsoo.gptmobile.data.sync.model.SyncOperation
import dev.chungjungsoo.gptmobile.data.sync.model.SyncStatusSnapshot
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavRemoteFile
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun syncStatusCard_withoutSnapshot_showsEmptyState() {
        setContentWithSnapshot(snapshot = null)

        composeRule.onNodeWithTag(SYNC_STATUS_CARD_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("同步状态").assertIsDisplayed()
        composeRule.onNodeWithText("显示最近一次本地备份、恢复、云端同步和连接测试结果").assertIsDisplayed()
        composeRule.onNodeWithText("尚无记录").assertIsDisplayed()
    }

    @Test
    fun syncStatusCard_withSnapshot_showsPersistedFields() {
        setContentWithSnapshot(
            snapshot = SyncStatusSnapshot(
                lastLocalExportAt = 100L,
                lastLocalRestoreAt = 200L,
                lastCloudUploadAt = 300L,
                lastCloudDownloadAt = 400L,
                lastConnectionTestAt = 500L,
                lastConnectionTestSuccess = true,
                lastOperation = SyncOperation.CLOUD_UPLOAD,
                lastOperationAt = 300L,
                lastOperationSuccess = true,
                lastRemoteFileName = "remote.json"
            )
        )

        composeRule.onNodeWithTag(SYNC_STATUS_CARD_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("remote.json").assertIsDisplayed()
        composeRule.onNodeWithTag(SYNC_STATUS_LAST_EXPORT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SYNC_STATUS_LAST_RESTORE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SYNC_STATUS_LAST_UPLOAD_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SYNC_STATUS_LAST_DOWNLOAD_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SYNC_STATUS_LAST_CONNECTION_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun syncStatusCard_showsStableErrorMessage() {
        setContentWithSnapshot(
            snapshot = SyncStatusSnapshot(
                lastOperation = SyncOperation.CLOUD_UPLOAD,
                lastOperationAt = 100L,
                lastOperationSuccess = false,
                lastErrorCategory = SyncErrorCategory.WEBDAV_AUTH_FAILED
            )
        )

        composeRule.onNodeWithText("WebDAV 认证失败，请检查账号或密码").assertIsDisplayed()
    }

    @Test
    fun syncStatusCard_showsBackupFileInvalidStableErrorMessage() {
        setContentWithSnapshot(
            snapshot = SyncStatusSnapshot(
                lastOperation = SyncOperation.LOCAL_RESTORE,
                lastOperationAt = 100L,
                lastOperationSuccess = false,
                lastErrorCategory = SyncErrorCategory.BACKUP_FILE_INVALID
            )
        )

        composeRule.onNodeWithText("备份文件无效，请检查内容格式").assertIsDisplayed()
    }

    @Test
    fun syncStatusCard_isPositionedBeforeWebDavSectionTitle() {
        setContentWithSnapshot(snapshot = null)

        val cardTop = composeRule.onNodeWithTag(SYNC_STATUS_CARD_TAG)
            .fetchSemanticsNode()
            .boundsInRoot.top
        val webDavTitleTop = composeRule.onNodeWithTag(SYNC_WEBDAV_SECTION_TITLE_TAG)
            .fetchSemanticsNode()
            .boundsInRoot.top

        assertTrue("同步状态卡片应位于 WebDAV 标题之前", cardTop < webDavTitleTop)
    }

    @Test
    fun importFromFileButton_isDisplayed() {
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(),
            appContext = ApplicationProvider.getApplicationContext()
        )

        composeRule.setContent {
            GPTMobileTheme {
                SyncScreen(
                    onNavigationClick = {},
                    syncViewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithTag(IMPORT_BACKUP_FROM_FILE_BUTTON_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clickSaveToFileWithoutBackup_showsPrompt() {
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(),
            appContext = ApplicationProvider.getApplicationContext()
        )

        composeRule.setContent {
            GPTMobileTheme {
                SyncScreen(
                    onNavigationClick = {},
                    syncViewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithTag(SAVE_BACKUP_TO_FILE_BUTTON_TAG).performScrollTo().performClick()
    }

    @Test
    fun conflictDialog_useRemoteBackup_loadsRemoteBackupMetadata() {
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(
                conflict = SyncConflict(
                    localSummary = BackupSummary(1, 2, 3, true),
                    remoteSummary = BackupSummary(4, 5, 6, true),
                    localExportedAt = 10L,
                    remoteExportedAt = 20L,
                    remoteFileName = "remote.json"
                ),
                remoteBackupContent = VALID_BACKUP_JSON
            ),
            appContext = ApplicationProvider.getApplicationContext()
        )

        composeRule.setContent {
            GPTMobileTheme {
                SyncScreen(
                    onNavigationClick = {},
                    syncViewModel = viewModel
                )
            }
        }

        composeRule.runOnIdle {
            viewModel.updateWebDavPassword("dav")
        }

        composeRule.onNodeWithText("上传到云端").performScrollTo().performClick()
        composeRule.onNodeWithText("以云端为准").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("remote.json").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun localTab_recoveryConfirmation_showsMetadataAndActions() {
        val exportedAt = 1_710_000_000_000L

        setContentWithImportedBackup(
            fileName = "backup.json",
            exportedAt = exportedAt
        )

        composeRule.onNodeWithText("backup.json").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(formatExpectedTimestamp(exportedAt)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("恢复会覆盖当前本地数据，确认前建议先导出一份最新备份。")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(RESTORE_CURRENT_DATA_BEFORE_CONFIRM_BUTTON_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("先备份当前数据").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(CONFIRM_RESTORE_IMPORTED_BACKUP_BUTTON_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("确认恢复").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun webDavTab_remoteFileItem_showsNameTimeAndSize() {
        val viewModel = setContentWithRemoteBackups(
            remoteBackups = listOf(
                WebDavRemoteFile(
                    path = "/dav/remote-a.json",
                    name = "remote-a.json",
                    modifiedAt = "2026-04-02 10:00",
                    contentLength = 2048
                )
            )
        )

        openWebDavTabAndRefresh(viewModel)

        composeRule.onNodeWithText("remote-a.json").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2026-04-02 10:00").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2048 B").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun webDavTab_deleteRemoteBackup_showsConfirmDialog() {
        val viewModel = setContentWithRemoteBackups(
            remoteBackups = listOf(
                WebDavRemoteFile(
                    path = "/dav/remote-a.json",
                    name = "remote-a.json",
                    modifiedAt = "2026-04-02 10:00",
                    contentLength = 2048
                )
            )
        )

        openWebDavTabAndRefresh(viewModel)

        composeRule.onNodeWithTag(remoteBackupDeleteButtonTag("remote-a.json"))
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("删除云端备份").assertIsDisplayed()
        composeRule.onNodeWithText("确定删除云端备份“remote-a.json”吗？")
            .assertIsDisplayed()
    }

    @Test
    fun webDavTab_confirmDeleteSelectedRemoteBackup_refreshesListAndUpdatesSelection() {
        val viewModel = setContentWithRemoteBackups(
            remoteBackups = listOf(
                WebDavRemoteFile(
                    path = "/dav/remote-a.json",
                    name = "remote-a.json",
                    modifiedAt = "2026-04-02 10:00",
                    contentLength = 2048
                ),
                WebDavRemoteFile(
                    path = "/dav/remote-b.json",
                    name = "remote-b.json",
                    modifiedAt = "2026-04-02 11:00",
                    contentLength = 4096
                )
            ),
            remoteBackupContents = mapOf(
                "remote-b.json" to validBackupJson(exportedAt = 2_222L)
            )
        )

        openWebDavTabAndRefresh(viewModel)

        composeRule.onNodeWithTag(remoteBackupDeleteButtonTag("remote-a.json"))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(CONFIRM_DELETE_REMOTE_BACKUP_BUTTON_TAG)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("remote-a.json").assertCountEquals(0)
        composeRule.onNodeWithText("从云端下载").performScrollTo().performClick()
        composeRule.onNodeWithText("remote-b.json").performScrollTo().assertIsDisplayed()
    }

    private fun setContentWithSnapshot(snapshot: SyncStatusSnapshot?) {
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(),
            settingRepository = FakeSettingRepository(initialSnapshot = snapshot),
            appContext = ApplicationProvider.getApplicationContext()
        )

        composeRule.setContent {
            GPTMobileTheme {
                SyncScreen(
                    onNavigationClick = {},
                    syncViewModel = viewModel
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun setContentWithImportedBackup(fileName: String, exportedAt: Long) {
        val backupJson = validBackupJson(exportedAt)
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(),
            appContext = ApplicationProvider.getApplicationContext()
        )

        composeRule.setContent {
            GPTMobileTheme {
                SyncScreen(
                    onNavigationClick = {},
                    syncViewModel = viewModel
                )
            }
        }

        composeRule.runOnIdle {
            viewModel.importBackupFile(fileName, backupJson)
        }
        composeRule.waitForIdle()
    }

    private fun setContentWithRemoteBackups(
        remoteBackups: List<WebDavRemoteFile>,
        remoteBackupContents: Map<String, String> = emptyMap()
    ): SyncViewModel {
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(
                remoteBackups = remoteBackups,
                remoteBackupContents = remoteBackupContents
            ),
            appContext = ApplicationProvider.getApplicationContext()
        )

        composeRule.setContent {
            GPTMobileTheme {
                SyncScreen(
                    onNavigationClick = {},
                    syncViewModel = viewModel
                )
            }
        }
        composeRule.waitForIdle()
        return viewModel
    }

    private fun openWebDavTabAndRefresh(viewModel: SyncViewModel) {
        composeRule.runOnIdle {
            viewModel.updateWebDavPassword("dav-password")
        }
        composeRule.onNodeWithTag(SYNC_TAB_WEBDAV_TAG).performClick()
        composeRule.onNodeWithText("刷新云端备份").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun formatExpectedTimestamp(timestamp: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))

    private fun validBackupJson(exportedAt: Long): String =
        "{\"schemaVersion\":1,\"exportedAt\":$exportedAt,\"appVersion\":\"test\",\"backupType\":\"full\",\"summary\":{\"chatRoomCount\":0,\"messageCount\":0,\"aiMaskCount\":0,\"containsSecrets\":false},\"payload\":{\"settings\":{\"platforms\":[],\"theme\":{\"dynamicTheme\":\"OFF\",\"themeMode\":\"SYSTEM\"},\"streamingStyle\":{\"value\":0,\"name\":\"TYPEWRITER\"}},\"database\":{\"chatRooms\":[],\"messages\":[],\"aiMasks\":[]}}}"

    private class FakeSettingRepository(
        private val initialSnapshot: SyncStatusSnapshot? = null
    ) : SettingRepository {
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

        override suspend fun fetchSyncStatusSnapshot(): SyncStatusSnapshot? = initialSnapshot

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

        override suspend fun updateSyncStatusSnapshot(snapshot: SyncStatusSnapshot?) = Unit
    }

    private class FakeSyncRepository(
        private val conflict: SyncConflict? = null,
        private val remoteBackupContent: String = "",
        remoteBackups: List<WebDavRemoteFile> = emptyList(),
        private val remoteBackupContents: Map<String, String> = emptyMap()
    ) : SyncRepository {
        private val mutableRemoteBackups = remoteBackups.toMutableList()

        override suspend fun exportBackupJson(): String = "{\"backup\":true}"

        override suspend fun restoreBackupJson(content: String) = Unit

        override suspend fun parseBackup(content: String): BackupFile = BackupFile(
            schemaVersion = 1,
            exportedAt = Regex("\\\"exportedAt\\\":(\\d+)")
                .find(content)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?: 1_234L,
            appVersion = "test",
            backupType = "local",
            summary = BackupSummary(
                chatRoomCount = 0,
                messageCount = 0,
                aiMaskCount = 0,
                containsSecrets = false
            ),
            payload = dev.chungjungsoo.gptmobile.data.sync.model.BackupPayload(
                settings = dev.chungjungsoo.gptmobile.data.sync.model.BackupSettings(
                    platforms = emptyList(),
                    theme = dev.chungjungsoo.gptmobile.data.sync.model.BackupThemeSetting(
                        dynamicTheme = "OFF",
                        themeMode = "SYSTEM"
                    ),
                    streamingStyle = dev.chungjungsoo.gptmobile.data.sync.model.BackupStreamingStyle(
                        value = 0,
                        name = "TYPEWRITER"
                    )
                ),
                database = dev.chungjungsoo.gptmobile.data.sync.model.BackupDatabase(
                    chatRooms = emptyList(),
                    messages = emptyList(),
                    aiMasks = emptyList()
                )
            )
        )

        override suspend fun testWebDavConnection(baseUrl: String, username: String, remotePath: String, password: String) = Unit

        override suspend fun saveWebDavConfig(baseUrl: String, username: String, remotePath: String, password: String) = Unit

        override suspend fun clearWebDavConfig() = Unit

        override suspend fun getWebDavConfig(): WebDavConfig? = null

        override suspend fun getWebDavPassword(): String? = null

        override suspend fun listRemoteBackups(password: String): List<WebDavRemoteFile> = mutableRemoteBackups.toList()

        override suspend fun detectUploadConflict(password: String): SyncConflict? = conflict

        override suspend fun uploadBackup(password: String, overwrite: Boolean): String = ""

        override suspend fun downloadRemoteBackup(password: String, remoteFileName: String): String =
            remoteBackupContents[remoteFileName] ?: remoteBackupContent

        override suspend fun deleteRemoteBackup(password: String, remotePath: String) {
            mutableRemoteBackups.removeAll { it.path == remotePath }
        }
    }

    private companion object {
        const val VALID_BACKUP_JSON = "{\"schemaVersion\":1,\"exportedAt\":1234,\"appVersion\":\"test\",\"backupType\":\"full\",\"summary\":{\"chatRoomCount\":0,\"messageCount\":0,\"aiMaskCount\":0,\"containsSecrets\":false},\"payload\":{\"settings\":{\"platforms\":[],\"theme\":{\"dynamicTheme\":\"OFF\",\"themeMode\":\"SYSTEM\"},\"streamingStyle\":{\"value\":0,\"name\":\"TYPEWRITER\"}},\"database\":{\"chatRooms\":[],\"messages\":[],\"aiMasks\":[]}}}"
    }
}
