package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.chungjungsoo.gptmobile.data.database.entity.AiMask

@Dao
interface AiMaskDao {

    @Query("SELECT * FROM ai_masks WHERE is_archived = 0 ORDER BY is_default DESC, group_name ASC, last_used_at DESC, updated_at DESC")
    suspend fun getAll(): List<AiMask>

    @Query("SELECT * FROM ai_masks WHERE is_archived = 1 ORDER BY updated_at DESC")
    suspend fun getArchived(): List<AiMask>

    @Query("SELECT * FROM ai_masks ORDER BY mask_id ASC")
    suspend fun getAllIncludingArchived(): List<AiMask>

    @Query("SELECT * FROM ai_masks WHERE mask_id = :id LIMIT 1")
    suspend fun getById(id: Int): AiMask?

    @Query("SELECT * FROM ai_masks WHERE is_default = 1 LIMIT 1")
    suspend fun getDefault(): AiMask?

    @Query("SELECT * FROM ai_masks WHERE is_archived = 0 ORDER BY last_used_at DESC, updated_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AiMask>

    @Query("SELECT DISTINCT group_name FROM ai_masks WHERE is_archived = 0 ORDER BY CASE WHEN group_name = '默认' THEN 0 ELSE 1 END, group_name ASC")
    suspend fun getActiveGroupNames(): List<String>

    @Query("SELECT * FROM ai_masks WHERE is_archived = 0 AND group_name = :groupName ORDER BY is_default DESC, last_used_at DESC, updated_at DESC")
    suspend fun getByGroup(groupName: String): List<AiMask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mask: AiMask): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(masks: List<AiMask>)

    @Update
    suspend fun update(mask: AiMask)

    @Query("UPDATE ai_masks SET is_archived = 1, updated_at = :timestamp WHERE mask_id = :id")
    suspend fun archiveById(id: Int, timestamp: Long)

    @Query("UPDATE ai_masks SET is_archived = 0, updated_at = :timestamp WHERE mask_id = :id")
    suspend fun restoreById(id: Int, timestamp: Long)

    @Query("DELETE FROM ai_masks WHERE mask_id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM ai_masks")
    suspend fun deleteAll()

    @Query("UPDATE ai_masks SET last_used_at = :timestamp WHERE mask_id = :id")
    suspend fun touch(id: Int, timestamp: Long)
}
