package dev.chungjungsoo.gptmobile.data.database.projection

import androidx.room.ColumnInfo
import dev.chungjungsoo.gptmobile.data.database.entity.APITypeConverter
import dev.chungjungsoo.gptmobile.data.model.ApiType

data class MessageSearchResult(
    @ColumnInfo(name = "messageId")
    val messageId: Int,
    @ColumnInfo(name = "chatId")
    val chatId: Int,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    @ColumnInfo(name = "chatTitle")
    val chatTitle: String,
    @ColumnInfo(name = "maskId")
    val maskId: Int?,
    @ColumnInfo(name = "enabledPlatforms")
    val enabledPlatformsRaw: String,
    @ColumnInfo(name = "roleName")
    val roleName: String
) {
    fun enabledPlatformsArgument(): String {
        return APITypeConverter().fromString(enabledPlatformsRaw).joinToString(",") { it.name }
    }

    fun enabledPlatforms(): List<ApiType> {
        return APITypeConverter().fromString(enabledPlatformsRaw)
    }
}
