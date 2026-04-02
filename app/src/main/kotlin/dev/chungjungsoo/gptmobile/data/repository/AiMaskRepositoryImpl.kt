package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.ChatDatabase
import dev.chungjungsoo.gptmobile.data.database.DatabaseTransactionRunner
import dev.chungjungsoo.gptmobile.data.database.RoomDatabaseTransactionRunner
import dev.chungjungsoo.gptmobile.data.database.dao.AiMaskDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.entity.AiMask
import dev.chungjungsoo.gptmobile.data.model.RoleDefaults
import dev.chungjungsoo.gptmobile.data.model.RoleGroup
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AiMaskRepositoryImpl internal constructor(
    private val aiMaskDao: AiMaskDao,
    private val chatRoomDao: ChatRoomDao,
    private val transactionRunner: DatabaseTransactionRunner
) : AiMaskRepository {
    private val defaultRoleMutex = Mutex()

    @Inject
    constructor(
        chatDatabase: ChatDatabase,
        aiMaskDao: AiMaskDao,
        chatRoomDao: ChatRoomDao
    ) : this(
        aiMaskDao = aiMaskDao,
        chatRoomDao = chatRoomDao,
        transactionRunner = RoomDatabaseTransactionRunner(chatDatabase)
    )

    override suspend fun fetchAll(): List<AiMask> {
        fetchDefault()
        return aiMaskDao.getAll()
    }

    override suspend fun fetchArchived(): List<AiMask> {
        fetchDefault()
        return aiMaskDao.getArchived()
    }

    override suspend fun fetchGroupedActive(): List<RoleGroup> {
        return aiMaskDao.getActiveGroupNames().map { groupName ->
            RoleGroup(
                groupName = groupName,
                roles = aiMaskDao.getByGroup(groupName)
            )
        }
    }

    override suspend fun fetchRecent(limit: Int): List<AiMask> = aiMaskDao.getRecent(limit)

    override suspend fun fetchById(id: Int): AiMask? = aiMaskDao.getById(id)

    override suspend fun fetchDefault(): AiMask {
        return defaultRoleMutex.withLock {
            cleanupDuplicateDefaults(aiMaskDao.getDefaults())?.let { return@withLock it }

            val now = System.currentTimeMillis() / 1000
            aiMaskDao.insertDefaultIfMissing(
                name = RoleDefaults.DEFAULT_ROLE_NAME,
                systemPrompt = "",
                groupName = RoleDefaults.DEFAULT_ROLE_GROUP,
                updatedAt = now,
                lastUsedAt = 0
            )

            cleanupDuplicateDefaults(aiMaskDao.getDefaults())
                ?: error("默认角色创建失败")
        }
    }

    override suspend fun upsert(name: String, systemPrompt: String, groupName: String, id: Int?): AiMask {
        val now = System.currentTimeMillis() / 1000
        val trimmedName = name.trim()
        val trimmedPrompt = systemPrompt.trim()
        val trimmedGroupName = groupName.trim().ifBlank { RoleDefaults.UNGROUPED_ROLE_NAME }
        val existingMask = if (id != null) aiMaskDao.getById(id) else null

        val mask = AiMask(
            id = id ?: 0,
            name = trimmedName,
            systemPrompt = trimmedPrompt,
            groupName = if (existingMask?.isDefault == true) RoleDefaults.DEFAULT_ROLE_GROUP else trimmedGroupName,
            isDefault = existingMask?.isDefault == true,
            isArchived = existingMask?.isArchived == true,
            updatedAt = now,
            lastUsedAt = existingMask?.lastUsedAt ?: 0
        )

        val newId = aiMaskDao.insert(mask).toInt()
        return mask.copy(id = if (id == null || id == 0) newId else id)
    }

    override suspend fun archive(id: Int) {
        val target = aiMaskDao.getById(id) ?: return
        if (target.isDefault) return
        val now = System.currentTimeMillis() / 1000
        aiMaskDao.archiveById(id, now)
        chatRoomDao.archiveByMaskId(id)
    }

    override suspend fun restore(id: Int) {
        val target = aiMaskDao.getById(id) ?: return
        val now = System.currentTimeMillis() / 1000
        aiMaskDao.restoreById(id, now)
        if (!target.isDefault) {
            chatRoomDao.restoreByMaskId(id)
        }
    }

    override suspend fun deletePermanently(id: Int) {
        val target = aiMaskDao.getById(id) ?: return
        if (target.isDefault) return
        chatRoomDao.deleteByMaskId(id)
        aiMaskDao.deleteById(id)
    }

    override suspend fun touch(id: Int) {
        aiMaskDao.touch(id, System.currentTimeMillis() / 1000)
    }

    private suspend fun cleanupDuplicateDefaults(defaultMasks: List<AiMask>): AiMask? {
        val retainedDefault = defaultMasks.firstOrNull() ?: return null
        val duplicateIds = defaultMasks.drop(1).map { it.id }
        if (duplicateIds.isNotEmpty()) {
            transactionRunner.run {
                chatRoomDao.clearMaskIdByMaskIds(duplicateIds)
                aiMaskDao.deleteByIds(duplicateIds)
            }
        }
        return retainedDefault
    }
}
