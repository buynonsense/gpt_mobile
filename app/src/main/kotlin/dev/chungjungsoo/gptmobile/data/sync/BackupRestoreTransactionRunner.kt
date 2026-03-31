package dev.chungjungsoo.gptmobile.data.sync

import androidx.room.withTransaction
import dev.chungjungsoo.gptmobile.data.database.ChatDatabase

internal interface BackupRestoreTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

internal class RoomBackupRestoreTransactionRunner(
    private val chatDatabase: ChatDatabase
) : BackupRestoreTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = chatDatabase.withTransaction {
        block()
    }
}
