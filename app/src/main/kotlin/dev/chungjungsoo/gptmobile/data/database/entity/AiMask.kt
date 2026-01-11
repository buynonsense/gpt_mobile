package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 面具：用于预设 system prompt。
 */
@Entity(tableName = "ai_masks")
data class AiMask(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "mask_id")
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis() / 1000,

    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long = 0
)
