package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.entity.AiMask
import dev.chungjungsoo.gptmobile.data.model.RoleGroup

interface AiMaskRepository {
    suspend fun fetchAll(): List<AiMask>
    suspend fun fetchArchived(): List<AiMask>
    suspend fun fetchGroupedActive(): List<RoleGroup>
    suspend fun fetchRecent(limit: Int = 3): List<AiMask>
    suspend fun fetchById(id: Int): AiMask?
    suspend fun fetchDefault(): AiMask
    suspend fun upsert(name: String, systemPrompt: String, groupName: String = "未分组", id: Int? = null): AiMask
    suspend fun archive(id: Int)
    suspend fun restore(id: Int)
    suspend fun deletePermanently(id: Int)
    suspend fun touch(id: Int)
}
