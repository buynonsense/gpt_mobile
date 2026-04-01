package dev.chungjungsoo.gptmobile.data.sync

import dev.chungjungsoo.gptmobile.data.sync.model.BackupFile
import dev.chungjungsoo.gptmobile.data.sync.model.BackupSummary

interface BackupRepository {
    suspend fun exportBackup(): BackupFile
    suspend fun restoreBackup(fileContent: String)
    suspend fun parseBackupFile(fileContent: String): BackupFile
    suspend fun readSummary(fileContent: String): BackupSummary
}
