package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.entity.AiMask

interface AiMaskRepository {
    suspend fun fetchAll(): List<AiMask>
    suspend fun fetchRecent(limit: Int = 3): List<AiMask>
    suspend fun fetchById(id: Int): AiMask?
    suspend fun upsert(name: String, systemPrompt: String, id: Int? = null): AiMask
    suspend fun delete(id: Int)
    suspend fun touch(id: Int)
}
