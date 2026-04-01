package dev.chungjungsoo.gptmobile.data.sync.model

import dev.chungjungsoo.gptmobile.data.database.entity.AiMask
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.StreamingStyle
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val backupType: String,
    val summary: BackupSummary,
    val payload: BackupPayload
)

@Serializable
data class BackupSummary(
    val chatRoomCount: Int,
    val messageCount: Int,
    val aiMaskCount: Int,
    val containsSecrets: Boolean
)

@Serializable
data class BackupPayload(
    val settings: BackupSettings,
    val database: BackupDatabase
)

@Serializable
data class BackupSettings(
    val platforms: List<BackupPlatform>,
    val theme: BackupThemeSetting,
    val streamingStyle: BackupStreamingStyle
)

@Serializable
data class BackupDatabase(
    val chatRooms: List<BackupChatRoom>,
    val messages: List<BackupMessage>,
    val aiMasks: List<BackupAiMask>
)

@Serializable
data class BackupPlatform(
    val name: String,
    val selected: Boolean = false,
    val enabled: Boolean,
    val apiUrl: String,
    val token: String? = null,
    val model: String? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val systemPrompt: String? = null
)

@Serializable
data class BackupThemeSetting(
    val dynamicTheme: String,
    val themeMode: String
)

@Serializable
data class BackupStreamingStyle(
    val value: Int,
    val name: String
)

@Serializable
data class BackupChatRoom(
    val id: Int,
    val title: String,
    val enabledPlatform: List<String>,
    val maskId: Int? = null,
    val maskName: String? = null,
    val systemPrompt: String? = null,
    val isArchived: Boolean,
    val createdAt: Long
)

@Serializable
data class BackupMessage(
    val id: Int,
    val chatId: Int,
    val content: String,
    val imageData: String? = null,
    val linkedMessageId: Int,
    val platformType: String? = null,
    val modelName: String? = null,
    val createdAt: Long
)

@Serializable
data class BackupAiMask(
    val id: Int,
    val name: String,
    val systemPrompt: String,
    val groupName: String,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val updatedAt: Long,
    val lastUsedAt: Long
)

fun Platform.toBackupModel(): BackupPlatform = BackupPlatform(
    name = name.name,
    selected = selected,
    enabled = enabled,
    apiUrl = apiUrl,
    token = token,
    model = model,
    temperature = temperature,
    topP = topP,
    systemPrompt = systemPrompt
)

fun BackupPlatform.toPlatform(): Platform = Platform(
    name = ApiType.valueOf(name),
    selected = selected,
    enabled = enabled,
    apiUrl = apiUrl,
    token = token,
    model = model,
    temperature = temperature,
    topP = topP,
    systemPrompt = systemPrompt
)

fun ThemeSetting.toBackupModel(): BackupThemeSetting = BackupThemeSetting(
    dynamicTheme = dynamicTheme.name,
    themeMode = themeMode.name
)

fun BackupThemeSetting.toThemeSetting(): ThemeSetting = ThemeSetting(
    dynamicTheme = DynamicTheme.valueOf(dynamicTheme),
    themeMode = ThemeMode.valueOf(themeMode)
)

fun StreamingStyle.toBackupModel(): BackupStreamingStyle = BackupStreamingStyle(
    value = value,
    name = name
)

fun BackupStreamingStyle.toStreamingStyle(): StreamingStyle = StreamingStyle.valueOf(name)

fun ChatRoom.toBackupModel(): BackupChatRoom = BackupChatRoom(
    id = id,
    title = title,
    enabledPlatform = enabledPlatform.map { it.name },
    maskId = maskId,
    maskName = maskName,
    systemPrompt = systemPrompt,
    isArchived = isArchived,
    createdAt = createdAt
)

fun BackupChatRoom.toChatRoom(): ChatRoom = ChatRoom(
    id = id,
    title = title,
    enabledPlatform = enabledPlatform.map { ApiType.valueOf(it) },
    maskId = maskId,
    maskName = maskName,
    systemPrompt = systemPrompt,
    isArchived = isArchived,
    createdAt = createdAt
)

fun Message.toBackupModel(): BackupMessage = BackupMessage(
    id = id,
    chatId = chatId,
    content = content,
    imageData = imageData,
    linkedMessageId = linkedMessageId,
    platformType = platformType?.name,
    modelName = modelName,
    createdAt = createdAt
)

fun BackupMessage.toMessage(): Message = Message(
    id = id,
    chatId = chatId,
    content = content,
    imageData = imageData,
    linkedMessageId = linkedMessageId,
    platformType = platformType?.let { ApiType.valueOf(it) },
    modelName = modelName,
    createdAt = createdAt
)

fun AiMask.toBackupModel(): BackupAiMask = BackupAiMask(
    id = id,
    name = name,
    systemPrompt = systemPrompt,
    groupName = groupName,
    isDefault = isDefault,
    isArchived = isArchived,
    updatedAt = updatedAt,
    lastUsedAt = lastUsedAt
)

fun BackupAiMask.toAiMask(): AiMask = AiMask(
    id = id,
    name = name,
    systemPrompt = systemPrompt,
    groupName = groupName,
    isDefault = isDefault,
    isArchived = isArchived,
    updatedAt = updatedAt,
    lastUsedAt = lastUsedAt
)
