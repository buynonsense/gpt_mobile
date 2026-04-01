package dev.chungjungsoo.gptmobile.data.sync

import androidx.room.InvalidationTracker
import androidx.room.DatabaseConfiguration
import androidx.sqlite.db.SupportSQLiteOpenHelper
import dev.chungjungsoo.gptmobile.data.database.ChatDatabase
import dev.chungjungsoo.gptmobile.data.database.dao.AiMaskDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.entity.AiMask
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.StreamingStyle
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.sync.model.BackupFile
import dev.chungjungsoo.gptmobile.data.sync.model.SyncStatusSnapshot
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRepositoryImplTest {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun exportBackup_setsSummaryCountsAndContainsSecrets_whenPlatformHasToken() = runBlocking {
        val repository = createRepository(
            platforms = listOf(
                Platform(
                    name = ApiType.OPENAI,
                    selected = true,
                    enabled = true,
                    apiUrl = "https://example.com/v1",
                    token = "secret-token",
                    model = "gpt-4o-mini"
                )
            ),
            chatRooms = listOf(
                ChatRoom(id = 1, title = "Chat 1", enabledPlatform = listOf(ApiType.OPENAI), createdAt = 10L),
                ChatRoom(id = 2, title = "Chat 2", enabledPlatform = listOf(ApiType.GOOGLE), createdAt = 20L)
            ),
            messages = listOf(
                Message(id = 1, chatId = 1, content = "Hello", linkedMessageId = 0, platformType = ApiType.OPENAI, createdAt = 11L),
                Message(id = 2, chatId = 1, content = "World", linkedMessageId = 1, platformType = ApiType.OPENAI, createdAt = 12L),
                Message(id = 3, chatId = 2, content = "Hi", linkedMessageId = 0, platformType = ApiType.GOOGLE, createdAt = 21L)
            ),
            aiMasks = listOf(
                AiMask(id = 1, name = "Planner", systemPrompt = "Plan carefully", updatedAt = 30L, lastUsedAt = 31L)
            )
        )

        val backup = repository.exportBackup("correct-password")

        assertEquals(2, backup.summary.chatRoomCount)
        assertEquals(3, backup.summary.messageCount)
        assertEquals(1, backup.summary.aiMaskCount)
        assertTrue(backup.summary.containsSecrets)
    }

    @Test
    fun exportBackup_setsEncryptionMetadataContract() = runBlocking {
        val repository = createRepository()

        val backup = repository.exportBackup("correct-password")

        assertEquals(1, backup.schemaVersion)
        assertTrue(backup.encryption.enabled)
        assertEquals("AES/GCM/NoPadding", backup.encryption.algorithm)
        assertEquals("PBKDF2WithHmacSHA256", backup.encryption.kdf)
        assertTrue(backup.encryption.iterations > 0)
        assertTrue(backup.encryption.salt.isNotBlank())
        assertTrue(backup.encryption.iv.isNotBlank())
        assertTrue(backup.payload.isNotBlank())
    }

    @Test
    fun parseBackupFile_throws_whenJsonIsInvalid() {
        assertThrows(SerializationException::class.java) {
            runBlocking {
                repository().parseBackupFile("not-json")
            }
        }
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun parseBackupFile_throws_whenRequiredFieldIsMissing() {
        assertThrows(MissingFieldException::class.java) {
            runBlocking {
                repository().parseBackupFile("""{"schemaVersion":1}""")
            }
        }
    }

    @Test
    fun parseBackupFile_throwsIllegalArgumentException_whenSchemaIsUnsupported() {
        val repository = createRepository()
        val exported = runBlocking {
            repository.exportBackup("correct-password")
        }

        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.parseBackupFile(exported.copy(schemaVersion = 99).toJson())
            }
        }

        assertTrue(exception.message.orEmpty().contains("Unsupported backup schema version"))
    }

    @Test
    fun restoreBackup_throwsInvalidBackupPassword_whenPasswordIsWrong() {
        val repository = createRepository()
        val exported = runBlocking {
            repository.exportBackup("correct-password")
        }

        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.restoreBackup(exported.toJson(), "wrong-password")
            }
        }

        assertEquals("Invalid backup password", exception.message)
    }

    @Test
    fun restoreBackup_replacesLocalDataAndUpdatesSettings() = runBlocking {
        val targetPlatform = platform(token = "secret-token")
        val targetTheme = ThemeSetting(
            dynamicTheme = DynamicTheme.ON,
            themeMode = ThemeMode.DARK
        )
        val targetStreamingStyle = StreamingStyle.FLASH
        val backupJson = createRepository(
            platforms = listOf(targetPlatform),
            themeSetting = targetTheme,
            streamingStyle = targetStreamingStyle,
            chatRooms = listOf(chatRoom(id = 1, title = "new")),
            messages = listOf(message(id = 11, chatId = 1)),
            aiMasks = listOf(mask(id = 21, name = "mask"))
        ).exportBackup("pw").toJson()

        val fakeChatRoomDao = createMutableChatRoomDao(listOf(chatRoom(id = 7, title = "old")))
        val fakeMessageDao = createMutableMessageDao(listOf(message(id = 99, chatId = 7, content = "stale")))
        val fakeAiMaskDao = createMutableAiMaskDao(listOf(mask(id = 31, name = "old-mask", systemPrompt = "old")))
        val fakeSettingRepository = RecordingSettingRepository()
        val repository = BackupRepositoryImpl(
            chatRoomDao = fakeChatRoomDao.dao,
            messageDao = fakeMessageDao.dao,
            aiMaskDao = fakeAiMaskDao.dao,
            settingRepository = fakeSettingRepository,
            cryptoManager = BackupCryptoManager(),
            restoreTransactionRunner = SnapshotBackupRestoreTransactionRunner(
                fakeChatRoomDao.store,
                fakeMessageDao.store,
                fakeAiMaskDao.store
            )
        )

        repository.restoreBackup(backupJson, "pw")

        assertEquals(listOf(chatRoom(id = 1, title = "new")), fakeChatRoomDao.currentData)
        assertEquals(listOf(message(id = 11, chatId = 1)), fakeMessageDao.currentData)
        assertEquals(listOf(mask(id = 21, name = "mask")), fakeAiMaskDao.currentData)
        assertEquals(listOf(targetPlatform), fakeSettingRepository.updatedPlatforms)
        assertEquals(targetTheme, fakeSettingRepository.updatedThemes)
        assertEquals(targetStreamingStyle, fakeSettingRepository.updatedStreamingStyle)
    }

    @Test
    fun restoreBackup_whenSettingsUpdateFails_locksCurrentPartialFailureBehavior() = runBlocking {
        val targetPlatform = platform(token = "secret-token")
        val backupJson = createRepository(
            platforms = listOf(targetPlatform),
            themeSetting = ThemeSetting(
                dynamicTheme = DynamicTheme.ON,
                themeMode = ThemeMode.DARK
            ),
            streamingStyle = StreamingStyle.FLASH,
            chatRooms = listOf(chatRoom(id = 1, title = "new")),
            messages = listOf(message(id = 11, chatId = 1)),
            aiMasks = listOf(mask(id = 21, name = "mask"))
        ).exportBackup("pw").toJson()

        val previousChat = chatRoom(id = 7, title = "old")
        val previousMessage = message(id = 99, chatId = 7, content = "stale")
        val previousMask = mask(id = 31, name = "old-mask", systemPrompt = "old")
        val fakeChatRoomDao = createMutableChatRoomDao(listOf(previousChat))
        val fakeMessageDao = createMutableMessageDao(listOf(previousMessage))
        val fakeAiMaskDao = createMutableAiMaskDao(listOf(previousMask))
        val fakeSettingRepository = RecordingSettingRepository(
            failOnUpdateThemes = true
        )
        val repository = BackupRepositoryImpl(
            chatRoomDao = fakeChatRoomDao.dao,
            messageDao = fakeMessageDao.dao,
            aiMaskDao = fakeAiMaskDao.dao,
            settingRepository = fakeSettingRepository,
            cryptoManager = BackupCryptoManager(),
            restoreTransactionRunner = SnapshotBackupRestoreTransactionRunner(
                fakeChatRoomDao.store,
                fakeMessageDao.store,
                fakeAiMaskDao.store
            )
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.restoreBackup(backupJson, "pw")
            }
        }

        assertEquals("theme update failed", exception.message)
        assertEquals(listOf(previousChat), fakeChatRoomDao.currentData)
        assertEquals(listOf(previousMessage), fakeMessageDao.currentData)
        assertEquals(listOf(previousMask), fakeAiMaskDao.currentData)
        assertEquals(listOf(targetPlatform), fakeSettingRepository.updatedPlatforms)
        assertNull(fakeSettingRepository.updatedThemes)
        assertNull(fakeSettingRepository.updatedStreamingStyle)
    }

    private fun repository(): BackupRepositoryImpl = createRepository()

    private fun chatRoom(id: Int, title: String): ChatRoom = ChatRoom(
        id = id,
        title = title,
        enabledPlatform = listOf(ApiType.OPENAI),
        createdAt = id.toLong()
    )

    private fun message(id: Int, chatId: Int, content: String = "hello"): Message = Message(
        id = id,
        chatId = chatId,
        content = content,
        linkedMessageId = 0,
        platformType = ApiType.OPENAI,
        createdAt = id.toLong()
    )

    private fun mask(id: Int, name: String, systemPrompt: String = "prompt"): AiMask = AiMask(
        id = id,
        name = name,
        systemPrompt = systemPrompt,
        updatedAt = id.toLong(),
        lastUsedAt = id.toLong()
    )

    private fun platform(token: String? = null): Platform = Platform(
        name = ApiType.OPENAI,
        selected = true,
        enabled = true,
        apiUrl = "https://example.com/v1",
        token = token,
        model = "gpt-4o-mini"
    )

    private fun createRepository(
        platforms: List<Platform> = emptyList(),
        themeSetting: ThemeSetting = ThemeSetting(
            dynamicTheme = DynamicTheme.OFF,
            themeMode = ThemeMode.SYSTEM
        ),
        streamingStyle: StreamingStyle = StreamingStyle.TYPEWRITER,
        chatRooms: List<ChatRoom> = emptyList(),
        messages: List<Message> = emptyList(),
        aiMasks: List<AiMask> = emptyList()
    ): BackupRepositoryImpl {
        return BackupRepositoryImpl(
            chatDatabase = UnusedChatDatabase,
            chatRoomDao = createChatRoomDao(chatRooms),
            messageDao = createMessageDao(messages),
            aiMaskDao = createAiMaskDao(aiMasks),
            settingRepository = createSettingRepository(
                platforms = platforms,
                themeSetting = themeSetting,
                streamingStyle = streamingStyle
            ),
            cryptoManager = BackupCryptoManager()
        )
    }

    private fun BackupFile.toJson(): String = json.encodeToString(this)

    private fun createChatRoomDao(chatRooms: List<ChatRoom>): ChatRoomDao {
        return createProxy("ChatRoomDao") { methodName, _ ->
            when (methodName) {
                "getAll" -> chatRooms
                else -> unsupportedCall("ChatRoomDao", methodName)
            }
        }
    }

    private fun createMessageDao(messages: List<Message>): MessageDao {
        return createProxy("MessageDao") { methodName, _ ->
            when (methodName) {
                "getAll" -> messages
                else -> unsupportedCall("MessageDao", methodName)
            }
        }
    }

    private fun createAiMaskDao(aiMasks: List<AiMask>): AiMaskDao {
        return createProxy("AiMaskDao") { methodName, _ ->
            when (methodName) {
                "getAllIncludingArchived" -> aiMasks
                else -> unsupportedCall("AiMaskDao", methodName)
            }
        }
    }

    private fun createSettingRepository(
        platforms: List<Platform>,
        themeSetting: ThemeSetting,
        streamingStyle: StreamingStyle
    ): SettingRepository {
        return createProxy("SettingRepository") { methodName, _ ->
            when (methodName) {
                "fetchPlatforms" -> platforms
                "fetchThemes" -> themeSetting
                "fetchStreamingStyle" -> streamingStyle
                "fetchWebDavConfig" -> null
                else -> unsupportedCall("SettingRepository", methodName)
            }
        }
    }

    private fun createMutableChatRoomDao(initialData: List<ChatRoom>): MutableChatRoomDao {
        val store = MutableEntityStore(initialData)
        val dao = createProxy<ChatRoomDao>("MutableChatRoomDao") { methodName, args ->
            when (methodName) {
                "getAll" -> store.currentData
                "deleteAll" -> {
                    store.clear()
                    Unit
                }
                "insertAll" -> {
                    @Suppress("UNCHECKED_CAST")
                    store.addAll(args[0] as List<ChatRoom>)
                    Unit
                }
                else -> unsupportedCall("MutableChatRoomDao", methodName)
            }
        }

        return MutableChatRoomDao(dao = dao, store = store)
    }

    private fun createMutableMessageDao(initialData: List<Message>): MutableMessageDao {
        val store = MutableEntityStore(initialData)
        val dao = createProxy<MessageDao>("MutableMessageDao") { methodName, args ->
            when (methodName) {
                "getAll" -> store.currentData
                "deleteAll" -> {
                    store.clear()
                    Unit
                }
                "insertAll" -> {
                    @Suppress("UNCHECKED_CAST")
                    store.addAll(args[0] as List<Message>)
                    Unit
                }
                else -> unsupportedCall("MutableMessageDao", methodName)
            }
        }

        return MutableMessageDao(dao = dao, store = store)
    }

    private fun createMutableAiMaskDao(initialData: List<AiMask>): MutableAiMaskDao {
        val store = MutableEntityStore(initialData)
        val dao = createProxy<AiMaskDao>("MutableAiMaskDao") { methodName, args ->
            when (methodName) {
                "getAllIncludingArchived" -> store.currentData
                "deleteAll" -> {
                    store.clear()
                    Unit
                }
                "insertAll" -> {
                    @Suppress("UNCHECKED_CAST")
                    store.addAll(args[0] as List<AiMask>)
                    Unit
                }
                else -> unsupportedCall("MutableAiMaskDao", methodName)
            }
        }

        return MutableAiMaskDao(dao = dao, store = store)
    }

    private inline fun <reified T> createProxy(
        targetName: String,
        crossinline handler: (methodName: String, args: Array<out Any?>) -> Any?
    ): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { _, method, args ->
            when (method.name) {
                "toString" -> targetName
                "hashCode" -> System.identityHashCode(method)
                "equals" -> false
                else -> handler(method.name, args ?: emptyArray())
            }
        } as T
    }

    private class RecordingSettingRepository(
        private val failOnUpdateThemes: Boolean = false
    ) : SettingRepository {
        var updatedPlatforms: List<Platform>? = null
            private set

        var updatedThemes: ThemeSetting? = null
            private set

        var updatedStreamingStyle: StreamingStyle? = null
            private set

        override suspend fun fetchPlatforms(): List<Platform> = emptyList()

        override suspend fun fetchThemes(): ThemeSetting = ThemeSetting(
            dynamicTheme = DynamicTheme.OFF,
            themeMode = ThemeMode.SYSTEM
        )

        override suspend fun fetchStreamingStyle(): StreamingStyle = StreamingStyle.TYPEWRITER

        override suspend fun fetchWebDavConfig(): WebDavConfig? = null

        override suspend fun fetchSyncStatusSnapshot(): SyncStatusSnapshot? = null

        override suspend fun updatePlatforms(platforms: List<Platform>) {
            updatedPlatforms = platforms
        }

        override suspend fun updateThemes(themeSetting: ThemeSetting) {
            if (failOnUpdateThemes) {
                throw IllegalStateException("theme update failed")
            }
            updatedThemes = themeSetting
        }

        override suspend fun updateStreamingStyle(style: StreamingStyle) {
            updatedStreamingStyle = style
        }

        override suspend fun updateWebDavConfig(config: WebDavConfig?) = Unit

        override suspend fun updateSyncStatusSnapshot(snapshot: SyncStatusSnapshot?) = Unit
    }

    private class MutableEntityStore<T>(initialData: List<T>) {
        private val data = initialData.toMutableList()

        val currentData: List<T>
            get() = data.toList()

        fun clear() {
            data.clear()
        }

        fun addAll(items: List<T>) {
            data.addAll(items)
        }

        fun snapshot(): List<T> = data.toList()

        fun restore(snapshot: List<T>) {
            data.clear()
            data.addAll(snapshot)
        }

        fun snapshotAny(): List<Any?> = data.toList()

        @Suppress("UNCHECKED_CAST")
        fun restoreAny(snapshot: List<Any?>) {
            data.clear()
            data.addAll(snapshot as List<T>)
        }
    }

    private class SnapshotBackupRestoreTransactionRunner(
        private vararg val stores: MutableEntityStore<*>
    ) : BackupRestoreTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T {
            val snapshots = stores.map { it.snapshotAny() }
            return try {
                block()
            } catch (throwable: Throwable) {
                stores.zip(snapshots).forEach { (store, snapshot) ->
                    store.restoreAny(snapshot)
                }
                throw throwable
            }
        }
    }

    private data class MutableChatRoomDao(
        val dao: ChatRoomDao,
        val store: MutableEntityStore<ChatRoom>
    ) {
        val currentData: List<ChatRoom>
            get() = store.currentData
    }

    private data class MutableMessageDao(
        val dao: MessageDao,
        val store: MutableEntityStore<Message>
    ) {
        val currentData: List<Message>
            get() = store.currentData
    }

    private data class MutableAiMaskDao(
        val dao: AiMaskDao,
        val store: MutableEntityStore<AiMask>
    ) {
        val currentData: List<AiMask>
            get() = store.currentData
    }

    private fun unsupportedCall(targetName: String, methodName: String): Nothing {
        throw UnsupportedOperationException("测试未使用 $targetName.$methodName()")
    }

    private object UnusedChatDatabase : ChatDatabase() {
        override fun chatRoomDao(): ChatRoomDao = throw UnsupportedOperationException("测试未使用 chatRoomDao()")

        override fun messageDao(): MessageDao = throw UnsupportedOperationException("测试未使用 messageDao()")

        override fun aiMaskDao(): AiMaskDao = throw UnsupportedOperationException("测试未使用 aiMaskDao()")

        override fun clearAllTables() = Unit

        override fun createInvalidationTracker(): InvalidationTracker = InvalidationTracker(this)

        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
            throw UnsupportedOperationException("测试未使用 openHelper")
        }
    }
}
