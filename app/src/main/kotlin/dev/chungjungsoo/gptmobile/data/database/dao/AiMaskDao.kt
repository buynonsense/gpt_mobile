package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.chungjungsoo.gptmobile.data.database.entity.AiMask

@Dao
interface AiMaskDao {

    @Query("SELECT * FROM ai_masks ORDER BY updated_at DESC")
    suspend fun getAll(): List<AiMask>

    @Query("SELECT * FROM ai_masks WHERE mask_id = :id LIMIT 1")
    suspend fun getById(id: Int): AiMask?

    @Query("SELECT * FROM ai_masks ORDER BY last_used_at DESC, updated_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AiMask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mask: AiMask): Long

    @Update
    suspend fun update(mask: AiMask)

    @Query("DELETE FROM ai_masks WHERE mask_id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE ai_masks SET last_used_at = :timestamp WHERE mask_id = :id")
    suspend fun touch(id: Int, timestamp: Long)
}
