package dev.chungjungsoo.gptmobile.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.chungjungsoo.gptmobile.data.sync.model.SyncErrorCategory
import dev.chungjungsoo.gptmobile.data.sync.model.SyncOperation
import dev.chungjungsoo.gptmobile.data.sync.model.SyncStatusSnapshot
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingDataSourceImplSyncStatusTest {
    @Test
    fun syncStatusSnapshot_roundTripsThroughDataStore() = runBlocking {
        val dataSource = createDataSource()
        val snapshot = SyncStatusSnapshot(
            lastLocalExportAt = 100L,
            lastLocalRestoreAt = 200L,
            lastCloudUploadAt = 300L,
            lastCloudDownloadAt = 400L,
            lastConnectionTestAt = 500L,
            lastConnectionTestSuccess = true,
            lastOperation = SyncOperation.CLOUD_UPLOAD,
            lastOperationAt = 600L,
            lastOperationSuccess = false,
            lastErrorCategory = SyncErrorCategory.WEBDAV_SERVER_ERROR,
            lastRemoteFileName = "backup.json"
        )

        dataSource.updateSyncStatusSnapshot(snapshot)

        assertEquals(snapshot, dataSource.getSyncStatusSnapshot())
    }

    @Test
    fun updateSyncStatusSnapshot_withNull_clearsStoredSnapshot() = runBlocking {
        val dataSource = createDataSource()
        val snapshot = SyncStatusSnapshot(
            lastOperation = SyncOperation.CLOUD_DOWNLOAD,
            lastOperationAt = 700L,
            lastOperationSuccess = true,
            lastRemoteFileName = "remote-backup.json"
        )

        dataSource.updateSyncStatusSnapshot(snapshot)
        dataSource.updateSyncStatusSnapshot(null)

        assertEquals(null, dataSource.getSyncStatusSnapshot())
    }

    private fun createDataSource(): SettingDataSourceImpl {
        val preferencesFile = File.createTempFile("sync-status", ".preferences_pb").apply {
            deleteOnExit()
        }
        return SettingDataSourceImpl(
            dataStore = PreferenceDataStoreFactory.create(
                produceFile = { preferencesFile }
            )
        )
    }
}
