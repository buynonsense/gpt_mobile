package dev.chungjungsoo.gptmobile.presentation.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.data.database.entity.AiMask
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.database.projection.MessageSearchResult
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.RoleDefaults
import dev.chungjungsoo.gptmobile.data.model.RoleGroup
import dev.chungjungsoo.gptmobile.data.repository.AiMaskRepository
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun defaultRoleQuickLaunchBar_staysPinnedAfterScrollingRoleList() {
        val defaultRole = createRole(
            id = 1,
            name = RoleDefaults.DEFAULT_ROLE_NAME,
            groupName = RoleDefaults.DEFAULT_ROLE_GROUP,
            isDefault = true,
            systemPrompt = ""
        )
        val customRoles = (1..18).map { index ->
            createRole(
                id = index + 1,
                name = "角色$index",
                groupName = "常用",
                systemPrompt = "提示词$index"
            )
        }
        val homeViewModel = HomeViewModel(
            chatRepository = FakeChatRepository(),
            aiMaskRepository = FakeAiMaskRepository(
                defaultRole = defaultRole,
                groupedRoles = listOf(
                    RoleGroup(groupName = defaultRole.groupName, roles = listOf(defaultRole)),
                    RoleGroup(groupName = "常用", roles = customRoles)
                )
            )
        )

        composeRule.setContent {
            GPTMobileTheme {
                HomeScreen(
                    homeViewModel = homeViewModel,
                    settingOnClick = {},
                    searchOnClick = {},
                    roleManagerOnClick = {},
                    onChatResolved = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HOME_DEFAULT_ROLE_QUICK_LAUNCH_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithText(RoleDefaults.DEFAULT_ROLE_NAME).assertCountEquals(1)

        val initialBounds = composeRule.onNodeWithTag(HOME_DEFAULT_ROLE_QUICK_LAUNCH_TAG)
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.onNodeWithText("角色18").performScrollTo().assertIsDisplayed()
        composeRule.waitForIdle()

        val scrolledBounds = composeRule.onNodeWithTag(HOME_DEFAULT_ROLE_QUICK_LAUNCH_TAG)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue("底部快速入口应固定在底部", abs(initialBounds.top - scrolledBounds.top) < 1f)
        assertTrue("底部快速入口底边位置不应随列表滚动变化", abs(initialBounds.bottom - scrolledBounds.bottom) < 1f)
    }

    private fun createRole(
        id: Int,
        name: String,
        groupName: String,
        isDefault: Boolean = false,
        systemPrompt: String
    ): AiMask {
        return AiMask(
            id = id,
            name = name,
            systemPrompt = systemPrompt,
            groupName = groupName,
            isDefault = isDefault,
            updatedAt = id.toLong()
        )
    }

    private class FakeAiMaskRepository(
        private val defaultRole: AiMask,
        private val groupedRoles: List<RoleGroup>
    ) : AiMaskRepository {
        override suspend fun fetchAll(): List<AiMask> = groupedRoles.flatMap { it.roles }

        override suspend fun fetchArchived(): List<AiMask> = emptyList()

        override suspend fun fetchGroupedActive(): List<RoleGroup> = groupedRoles

        override suspend fun fetchRecent(limit: Int): List<AiMask> = groupedRoles.flatMap { it.roles }.take(limit)

        override suspend fun fetchById(id: Int): AiMask? = groupedRoles.flatMap { it.roles }.firstOrNull { it.id == id }

        override suspend fun fetchDefault(): AiMask = defaultRole

        override suspend fun upsert(name: String, systemPrompt: String, groupName: String, id: Int?): AiMask {
            error("测试不需要创建或编辑角色")
        }

        override suspend fun archive(id: Int) {
            error("测试不需要归档角色")
        }

        override suspend fun restore(id: Int) {
            error("测试不需要恢复角色")
        }

        override suspend fun deletePermanently(id: Int) {
            error("测试不需要删除角色")
        }

        override suspend fun touch(id: Int) = Unit
    }

    private class FakeChatRepository : ChatRepository {
        override suspend fun completeOpenAIChat(question: Message, history: List<Message>, systemPrompt: String?, model: String?): Flow<ApiState> = emptyFlow()

        override suspend fun completeAnthropicChat(question: Message, history: List<Message>, systemPrompt: String?, model: String?): Flow<ApiState> = emptyFlow()

        override suspend fun completeGoogleChat(question: Message, history: List<Message>, systemPrompt: String?, model: String?): Flow<ApiState> = emptyFlow()

        override suspend fun completeGroqChat(question: Message, history: List<Message>, systemPrompt: String?, model: String?): Flow<ApiState> = emptyFlow()

        override suspend fun completeOllamaChat(question: Message, history: List<Message>, systemPrompt: String?, model: String?): Flow<ApiState> = emptyFlow()

        override suspend fun fetchChatRoom(chatId: Int): ChatRoom? = null

        override suspend fun fetchChatList(): List<ChatRoom> = emptyList()

        override suspend fun fetchArchivedChatList(): List<ChatRoom> = emptyList()

        override suspend fun fetchMessages(chatId: Int): List<Message> = emptyList()

        override suspend fun findOrCreateChatForRole(roleId: Int, enabledPlatforms: List<ApiType>?): ChatRoom {
            return ChatRoom(id = roleId, title = "chat-$roleId", enabledPlatform = emptyList(), maskId = roleId)
        }

        override suspend fun searchMessages(query: String, limit: Int): List<MessageSearchResult> = emptyList()

        override fun generateDefaultChatTitle(messages: List<Message>): String? = null

        override suspend fun updateChatTitle(chatRoom: ChatRoom, title: String) {
            error("测试不需要更新标题")
        }

        override suspend fun saveChat(chatRoom: ChatRoom, messages: List<Message>): ChatRoom {
            error("测试不需要保存会话")
        }

        override suspend fun deleteChats(chatRooms: List<ChatRoom>) {
            error("测试不需要删除会话")
        }

        override suspend fun fetchModels(apiType: ApiType, platformConfig: dev.chungjungsoo.gptmobile.data.dto.Platform?): List<String> = emptyList()
    }
}
