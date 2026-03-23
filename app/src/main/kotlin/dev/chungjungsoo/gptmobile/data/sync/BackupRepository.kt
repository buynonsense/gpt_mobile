package dev.chungjungsoo.gptmobile.data.sync

import dev.chungjungsoo.gptmobile.data.sync.model.BackupFile
import dev.chungjungsoo.gptmobile.data.sync.model.BackupSummary

interface BackupRepository {
    suspend fun exportBackup(password: String): BackupFile
    suspend fun restoreBackup(fileContent: String, password: String)
    suspend fun parseBackupFile(fileContent: String): BackupFile
    suspend fun readSummary(fileContent: String): BackupSummary
}
