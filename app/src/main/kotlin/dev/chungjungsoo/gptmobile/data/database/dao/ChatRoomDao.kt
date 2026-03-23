package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom

@Dao
interface ChatRoomDao {

    @Query("SELECT * FROM chats WHERE is_archived = 0 ORDER BY created_at DESC")
    suspend fun getChatRooms(): List<ChatRoom>

    @Query("SELECT * FROM chats WHERE chat_id = :id LIMIT 1")
    suspend fun getById(id: Int): ChatRoom?

    @Query("SELECT * FROM chats WHERE is_archived = 1 ORDER BY created_at DESC")
    suspend fun getArchivedChatRooms(): List<ChatRoom>

    @Query("SELECT * FROM chats ORDER BY chat_id ASC")
    suspend fun getAll(): List<ChatRoom>

    @Query("SELECT * FROM chats WHERE is_archived = 0 AND mask_id = :maskId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestActiveChatByMaskId(maskId: Int): ChatRoom?

    @Query("SELECT * FROM chats WHERE is_archived = 0 AND mask_id IS NULL ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestActiveDefaultChat(): ChatRoom?

    @Query("SELECT * FROM chats WHERE is_archived = 0 AND mask_id = :maskId ORDER BY created_at DESC")
    suspend fun getActiveChatsByMaskId(maskId: Int): List<ChatRoom>

    @Query("SELECT * FROM chats WHERE is_archived = 1 AND mask_id = :maskId ORDER BY created_at DESC")
    suspend fun getArchivedChatsByMaskId(maskId: Int): List<ChatRoom>

    @Query("UPDATE chats SET is_archived = 1 WHERE mask_id = :maskId")
    suspend fun archiveByMaskId(maskId: Int)

    @Query("UPDATE chats SET is_archived = 0 WHERE mask_id = :maskId")
    suspend fun restoreByMaskId(maskId: Int)

    @Query("UPDATE chats SET is_archived = 1 WHERE chat_id = :chatId")
    suspend fun archiveById(chatId: Int)

    @Query("DELETE FROM chats WHERE mask_id = :maskId")
    suspend fun deleteByMaskId(maskId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChatRoom(chatRoom: ChatRoom): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chatRooms: List<ChatRoom>)

    @Update
    suspend fun editChatRoom(chatRoom: ChatRoom)

    @Delete
    suspend fun deleteChatRooms(vararg chatRooms: ChatRoom)

    @Query("DELETE FROM chats")
    suspend fun deleteAll()
}
