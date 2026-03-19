package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.database.projection.MessageSearchResult

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chat_id=:chatInt")
    suspend fun loadMessages(chatInt: Int): List<Message>

    @Query(
        """
        SELECT
            messages.message_id AS messageId,
            messages.chat_id AS chatId,
            messages.content AS content,
            messages.created_at AS createdAt,
            chats.title AS chatTitle,
            chats.mask_id AS maskId,
            chats.enabled_platform AS enabledPlatforms,
            COALESCE(chats.mask_name, ai_masks.name, 'AI助手') AS roleName
        FROM messages
        INNER JOIN chats ON chats.chat_id = messages.chat_id
        LEFT JOIN ai_masks ON ai_masks.mask_id = chats.mask_id
        WHERE messages.content LIKE '%' || :query || '%'
        ORDER BY messages.created_at DESC
        LIMIT :limit
        """
    )
    suspend fun searchMessages(query: String, limit: Int = 100): List<MessageSearchResult>

    @Insert
    suspend fun addMessages(vararg messages: Message)

    @Update
    suspend fun editMessages(vararg message: Message)

    @Delete
    suspend fun deleteMessages(vararg message: Message)
}
