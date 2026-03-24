package dev.chungjungsoo.gptmobile.data.sync.model

import dev.chungjungsoo.gptmobile.data.database.entity.AiMask
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.model.ApiType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlinx.serialization.json.Json

class BackupModelsTest {
    @Test
    fun backupPlatform_deserializesLegacyJsonWithoutSelected_usingDefaultFalse() {
        val legacyJson = """
            {
                "name": "OPENAI",
                "enabled": true,
                "apiUrl": "https://example.com/v1",
                "token": "token-123",
                "model": "gpt-test",
                "temperature": 0.7,
                "topP": 0.9,
                "systemPrompt": "system prompt"
            }
        """.trimIndent()

        val backupPlatform = Json.decodeFromString<BackupPlatform>(legacyJson)

        assertFalse(backupPlatform.selected)
        assertEquals(
            BackupPlatform(
                name = "OPENAI",
                selected = false,
                enabled = true,
                apiUrl = "https://example.com/v1",
                token = "token-123",
                model = "gpt-test",
                temperature = 0.7f,
                topP = 0.9f,
                systemPrompt = "system prompt"
            ),
            backupPlatform
        )
    }

    @Test
    fun platform_roundTrip_preservesAllFields() {
        val platform = Platform(
            name = ApiType.OPENAI,
            selected = true,
            enabled = true,
            apiUrl = "https://example.com/v1",
            token = "token-123",
            model = "gpt-test",
            temperature = 0.7f,
            topP = 0.9f,
            systemPrompt = "system prompt"
        )

        val restoredPlatform = platform.toBackupModel().toPlatform()

        assertEquals(platform, restoredPlatform)
    }

    @Test
    fun chatRoomAndMessage_roundTrip_preservesKeyFields() {
        val chatRoom = ChatRoom(
            id = 7,
            title = "Chat title",
            enabledPlatform = listOf(ApiType.ANTHROPIC, ApiType.GOOGLE),
            maskId = 11,
            maskName = "Work",
            systemPrompt = "Stay concise",
            isArchived = true,
            createdAt = 1_234_567_890L
        )
        val message = Message(
            id = 13,
            chatId = 7,
            content = "Hello",
            imageData = "base64-image",
            linkedMessageId = 3,
            platformType = ApiType.GROQ,
            modelName = "llama-3.1",
            createdAt = 1_234_567_999L
        )

        val restoredChatRoom = chatRoom.toBackupModel().toChatRoom()
        val restoredMessage = message.toBackupModel().toMessage()

        assertEquals(chatRoom, restoredChatRoom)
        assertEquals(message, restoredMessage)
    }

    @Test
    fun aiMask_roundTrip_preservesKeyFields() {
        val aiMask = AiMask(
            id = 5,
            name = "Planner",
            systemPrompt = "Plan carefully",
            groupName = "Productivity",
            isDefault = true,
            isArchived = true,
            updatedAt = 1_700_000_000L,
            lastUsedAt = 1_700_000_123L
        )

        val restoredAiMask = aiMask.toBackupModel().toAiMask()

        assertEquals(aiMask, restoredAiMask)
    }
}
