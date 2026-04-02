package dev.chungjungsoo.gptmobile.data.database

import androidx.room.withTransaction

internal interface DatabaseTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

internal class RoomDatabaseTransactionRunner(
    private val chatDatabase: ChatDatabase
) : DatabaseTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = chatDatabase.withTransaction {
        block()
    }
}
