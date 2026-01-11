package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.dao.AiMaskDao
import dev.chungjungsoo.gptmobile.data.database.entity.AiMask
import javax.inject.Inject

class AiMaskRepositoryImpl @Inject constructor(
    private val aiMaskDao: AiMaskDao
) : AiMaskRepository {

    override suspend fun fetchAll(): List<AiMask> = aiMaskDao.getAll()

    override suspend fun fetchRecent(limit: Int): List<AiMask> = aiMaskDao.getRecent(limit)

    override suspend fun fetchById(id: Int): AiMask? = aiMaskDao.getById(id)

    override suspend fun upsert(name: String, systemPrompt: String, id: Int?): AiMask {
        val now = System.currentTimeMillis() / 1000
        val trimmedName = name.trim()
        val trimmedPrompt = systemPrompt.trim()

        val mask = AiMask(
            id = id ?: 0,
            name = trimmedName,
            systemPrompt = trimmedPrompt,
            updatedAt = now
        )

        val newId = aiMaskDao.insert(mask).toInt()
        return mask.copy(id = if (id == null || id == 0) newId else id)
    }

    override suspend fun delete(id: Int) {
        aiMaskDao.deleteById(id)
    }

    override suspend fun touch(id: Int) {
        aiMaskDao.touch(id, System.currentTimeMillis() / 1000)
    }
}
