package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.data.sync.SyncRepository
import dev.chungjungsoo.gptmobile.data.sync.model.BackupFile
import dev.chungjungsoo.gptmobile.data.sync.model.BackupSummary
import dev.chungjungsoo.gptmobile.data.sync.model.SyncConflict
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavRemoteFile
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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

        composeRule.onNodeWithTag(SYNC_ERROR_MESSAGE_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun conflictDialog_useRemoteBackup_loadsRemoteBackupIntoRestoreArea() {
        val viewModel = SyncViewModel(
            syncRepository = FakeSyncRepository(
                conflict = SyncConflict(
                    localSummary = BackupSummary(1, 2, 3, true),
                    remoteSummary = BackupSummary(4, 5, 6, true),
                    localExportedAt = 10L,
                    remoteExportedAt = 20L,
                    remoteFileName = "remote.json"
                ),
                remoteBackupContent = "{\"remote\":true}"
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
            viewModel.updateBackupPassword("backup")
            viewModel.updateWebDavPassword("dav")
        }

        composeRule.onNodeWithText("上传到云端").performScrollTo().performClick()
        composeRule.onNodeWithText("以云端为准").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("{\"remote\":true}").performScrollTo().assertIsDisplayed()
    }

    private class FakeSyncRepository(
        private val conflict: SyncConflict? = null,
        private val remoteBackupContent: String = ""
    ) : SyncRepository {
        override suspend fun exportBackupJson(password: String): String = ""

        override suspend fun restoreBackupJson(content: String, password: String) = Unit

        override suspend fun parseBackup(content: String): BackupFile = BackupFile(
            schemaVersion = 1,
            exportedAt = 0L,
            appVersion = "test",
            backupType = "local",
            summary = BackupSummary(
                chatRoomCount = 0,
                messageCount = 0,
                aiMaskCount = 0,
                containsSecrets = false
            ),
            encryption = dev.chungjungsoo.gptmobile.data.sync.model.BackupEncryption(
                enabled = false,
                algorithm = "",
                kdf = "",
                iterations = 0,
                salt = "",
                iv = ""
            ),
            payload = ""
        )

        override suspend fun testWebDavConnection(baseUrl: String, username: String, remotePath: String, password: String) = Unit

        override suspend fun saveWebDavConfig(baseUrl: String, username: String, remotePath: String, password: String) = Unit

        override suspend fun clearWebDavConfig() = Unit

        override suspend fun getWebDavConfig(): WebDavConfig? = null

        override suspend fun getWebDavPassword(): String? = null

        override suspend fun listRemoteBackups(password: String): List<WebDavRemoteFile> = emptyList()

        override suspend fun detectUploadConflict(password: String): SyncConflict? = conflict

        override suspend fun uploadBackup(password: String, overwrite: Boolean): String = ""

        override suspend fun downloadRemoteBackup(password: String, remoteFileName: String): String = remoteBackupContent
    }
}
