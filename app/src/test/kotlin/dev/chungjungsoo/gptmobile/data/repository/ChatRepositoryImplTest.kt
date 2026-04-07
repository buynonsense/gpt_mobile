package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import android.content.ContextWrapper
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.entity.AiMask
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.RoleDefaults
import dev.chungjungsoo.gptmobile.data.model.RoleGroup
import dev.chungjungsoo.gptmobile.data.model.StreamingStyle
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.sync.model.SyncStatusSnapshot
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatRepositoryImplTest {

    @Test
    fun findOrCreateChatForRole_whenExistingChatUsesOldPrompt_syncsLatestRolePrompt() = runBlocking {
        val role = AiMask(
            id = 7,
            name = "写作助手",
            systemPrompt = "你现在要用新的 prompt",
            groupName = "工作流",
            updatedAt = 100
        )
        val existingChat = ChatRoom(
            id = 11,
            title = "已有会话",
            enabledPlatform = listOf(ApiType.OPENAI),
            maskId = role.id,
            maskName = role.name,
            systemPrompt = "旧 prompt"
        )
        val fakeAiMaskRepository = FakeAiMaskRepository(role)
        val fakeChatRoomDao = RecordingChatRoomDao(existingChat)
        val repository = ChatRepositoryImpl(
            appContext = createStubContext(),
            aiMaskRepository = fakeAiMaskRepository,
            chatRoomDao = fakeChatRoomDao.dao,
            messageDao = createStubMessageDao(),
            settingRepository = FakeSettingRepository(),
            anthropic = FakeAnthropicApi()
        )

        val resolvedChat = repository.findOrCreateChatForRole(role.id)

        assertEquals("你现在要用新的 prompt", resolvedChat.systemPrompt)
        assertEquals("你现在要用新的 prompt", fakeChatRoomDao.latestChat?.systemPrompt)
        assertEquals(role.id, fakeAiMaskRepository.touchedRoleId)
    }

    @Test
    fun findOrCreateChatForRole_whenRolePromptBecomesBlank_clearsCachedPrompt() = runBlocking {
        val role = AiMask(
            id = 9,
            name = "AI助手",
            systemPrompt = "",
            groupName = RoleDefaults.DEFAULT_ROLE_GROUP,
            isDefault = true,
            updatedAt = 100
        )
        val existingChat = ChatRoom(
            id = 15,
            title = RoleDefaults.DEFAULT_ROLE_NAME,
            enabledPlatform = listOf(ApiType.OPENAI),
            maskId = null,
            maskName = RoleDefaults.DEFAULT_ROLE_NAME,
            systemPrompt = "旧的默认 prompt"
        )
        val repository = ChatRepositoryImpl(
            appContext = createStubContext(),
            aiMaskRepository = FakeAiMaskRepository(role),
            chatRoomDao = RecordingChatRoomDao(defaultChat = existingChat).dao,
            messageDao = createStubMessageDao(),
            settingRepository = FakeSettingRepository(),
            anthropic = FakeAnthropicApi()
        )

        val resolvedChat = repository.findOrCreateChatForRole(role.id)

        assertNull(resolvedChat.systemPrompt)
    }

    private fun createStubContext(): Context = ContextWrapper(null)

    private fun createStubMessageDao(): MessageDao {
        return Proxy.newProxyInstance(
            MessageDao::class.java.classLoader,
            arrayOf(MessageDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "toString" -> "StubMessageDao"
                "hashCode" -> 0
                "equals" -> false
                else -> error("MessageDao.${method.name} is not expected in this test")
            }
        } as MessageDao
    }

    private class RecordingChatRoomDao(
        existingChat: ChatRoom? = null,
        defaultChat: ChatRoom? = null
    ) {
        var latestChat: ChatRoom? = existingChat ?: defaultChat
            private set

        val dao: ChatRoomDao = Proxy.newProxyInstance(
            ChatRoomDao::class.java.classLoader,
            arrayOf(ChatRoomDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getLatestActiveChatByMaskId" -> latestChat?.takeIf { it.maskId == (args[0] as Number).toInt() }
                "getLatestActiveDefaultChat" -> latestChat?.takeIf { it.maskId == null }
                "editChatRoom" -> {
                    latestChat = args[0] as ChatRoom
                    Unit
                }
                "addChatRoom" -> error("测试不应创建新会话")
                "toString" -> "RecordingChatRoomDao"
                "hashCode" -> 0
                "equals" -> false
                else -> error("ChatRoomDao.${method.name} is not expected in this test")
            }
        } as ChatRoomDao
    }

    private class FakeAiMaskRepository(
        private val role: AiMask
    ) : AiMaskRepository {
        var touchedRoleId: Int? = null
            private set

        override suspend fun fetchAll(): List<AiMask> = listOf(role)

        override suspend fun fetchArchived(): List<AiMask> = emptyList()

        override suspend fun fetchGroupedActive(): List<RoleGroup> = error("测试不需要分组角色")

        override suspend fun fetchRecent(limit: Int): List<AiMask> = listOf(role)

        override suspend fun fetchById(id: Int): AiMask? = role.takeIf { it.id == id }

        override suspend fun fetchDefault(): AiMask = role

        override suspend fun upsert(name: String, systemPrompt: String, groupName: String, id: Int?): AiMask = error("测试不需要修改角色")

        override suspend fun archive(id: Int) = error("测试不需要归档角色")

        override suspend fun restore(id: Int) = error("测试不需要恢复角色")

        override suspend fun deletePermanently(id: Int) = error("测试不需要删除角色")

        override suspend fun touch(id: Int) {
            touchedRoleId = id
        }
    }

    private class FakeSettingRepository : SettingRepository {
        override suspend fun fetchPlatforms(): List<Platform> = emptyList()

        override suspend fun fetchThemes(): ThemeSetting = ThemeSetting()

        override suspend fun fetchStreamingStyle(): StreamingStyle = StreamingStyle.TYPEWRITER

        override suspend fun fetchWebDavConfig(): WebDavConfig? = null

        override suspend fun fetchSyncStatusSnapshot(): SyncStatusSnapshot? = null

        override suspend fun updatePlatforms(platforms: List<Platform>) = Unit

        override suspend fun updateThemes(themeSetting: ThemeSetting) = Unit

        override suspend fun updateStreamingStyle(style: StreamingStyle) = Unit

        override suspend fun updateWebDavConfig(config: WebDavConfig?) = Unit

        override suspend fun updateSyncStatusSnapshot(snapshot: SyncStatusSnapshot?) = Unit
    }

    private class FakeAnthropicApi : AnthropicAPI {
        override fun setToken(token: String?) = Unit

        override fun setAPIUrl(url: String) = Unit

        override suspend fun fetchModels(): List<String> = emptyList()

        override fun streamChatMessage(messageRequest: dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest): Flow<dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk> = emptyFlow()
    }
}
