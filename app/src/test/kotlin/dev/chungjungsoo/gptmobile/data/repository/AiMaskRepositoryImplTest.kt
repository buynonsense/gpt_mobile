package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.dao.AiMaskDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.entity.AiMask
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.DatabaseTransactionRunner
import dev.chungjungsoo.gptmobile.data.model.RoleDefaults
import java.lang.reflect.Proxy
import java.util.concurrent.BrokenBarrierException
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMaskRepositoryImplTest {
    @Test
    fun fetchDefault_whenCalledConcurrently_createsOnlyOneDefaultRole() = runBlocking {
        val fakeAiMaskDao = RecordingAiMaskDao(
            synchronizedEmptyDefaultReads = 2,
            synchronizedInsertDefaultIfMissingCalls = 2
        )
        val repository = createRepository(fakeAiMaskDao.dao)

        val results = listOf(
            async(Dispatchers.Default) { repository.fetchDefault() },
            async(Dispatchers.Default) { repository.fetchDefault() }
        ).awaitAll()

        assertEquals(1, fakeAiMaskDao.defaultMasks.size)
        assertEquals(results[0].id, results[1].id)
    }

    @Test
    fun fetchDefault_whenDuplicateDefaultsExist_keepsOnlyOneDefaultRole() = runBlocking {
        val fakeChatRoomDao = RecordingChatRoomDao(
            initialChats = listOf(
                ChatRoom(id = 9, title = "default", enabledPlatform = emptyList(), maskId = 2)
            )
        )
        val fakeAiMaskDao = RecordingAiMaskDao(
            initialMasks = listOf(
                defaultMask(id = 1, updatedAt = 10),
                defaultMask(id = 2, updatedAt = 20)
            )
        )
        val repository = createRepository(fakeAiMaskDao.dao, fakeChatRoomDao.dao)

        val result = repository.fetchDefault()

        assertEquals(1, fakeAiMaskDao.defaultMasks.size)
        assertEquals(1, fakeAiMaskDao.defaultMasks.single().id)
        assertEquals(1, result.id)
        assertEquals(null, fakeChatRoomDao.currentChats.single().maskId)
    }

    @Test
    fun fetchAll_whenDuplicateDefaultsExist_returnsDeduplicatedRoles() = runBlocking {
        val fakeAiMaskDao = RecordingAiMaskDao(
            initialMasks = listOf(
                defaultMask(id = 1, updatedAt = 10),
                defaultMask(id = 2, updatedAt = 20)
            )
        )
        val repository = createRepository(fakeAiMaskDao.dao)

        val roles = repository.fetchAll()

        assertEquals(1, roles.size)
        assertTrue(roles.single().isDefault)
        assertEquals(1, roles.single().id)
    }

    @Test
    fun deletePermanently_whenTargetIsDefaultRole_keepsRole() = runBlocking {
        val fakeAiMaskDao = RecordingAiMaskDao(
            initialMasks = listOf(defaultMask(id = 1, updatedAt = 10))
        )
        val repository = createRepository(fakeAiMaskDao.dao)

        repository.deletePermanently(1)

        assertEquals(listOf(1), fakeAiMaskDao.defaultMasks.map { it.id })
    }

    private fun createRepository(
        aiMaskDao: AiMaskDao,
        chatRoomDao: ChatRoomDao = RecordingChatRoomDao(emptyList()).dao,
        transactionRunner: DatabaseTransactionRunner = ImmediateTransactionRunner()
    ): AiMaskRepositoryImpl {
        return AiMaskRepositoryImpl(
            aiMaskDao = aiMaskDao,
            chatRoomDao = chatRoomDao,
            transactionRunner = transactionRunner
        )
    }

    private fun defaultMask(id: Int, updatedAt: Long): AiMask {
        return AiMask(
            id = id,
            name = RoleDefaults.DEFAULT_ROLE_NAME,
            systemPrompt = "",
            groupName = RoleDefaults.DEFAULT_ROLE_GROUP,
            isDefault = true,
            updatedAt = updatedAt
        )
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

    private fun unsupportedCall(targetName: String, methodName: String): Nothing {
        error("$targetName.$methodName is not expected in this test")
    }

    private class RecordingAiMaskDao(
        initialMasks: List<AiMask> = emptyList(),
        synchronizedEmptyDefaultReads: Int = 0,
        synchronizedInsertDefaultIfMissingCalls: Int = 0
    ) {
        private val lock = Any()
        private val masks = initialMasks.toMutableList()
        private val nextId = AtomicInteger((initialMasks.maxOfOrNull { it.id } ?: 0) + 1)
        private val emptyDefaultReadCount = AtomicInteger(0)
        private val emptyDefaultReadBarrier = synchronizedEmptyDefaultReads
            .takeIf { it > 1 }
            ?.let { CyclicBarrier(it) }
        private val insertDefaultIfMissingCount = AtomicInteger(0)
        private val insertDefaultIfMissingBarrier = synchronizedInsertDefaultIfMissingCalls
            .takeIf { it > 1 }
            ?.let { CyclicBarrier(it) }

        val dao: AiMaskDao = createProxy<AiMaskDao>("RecordingAiMaskDao") { methodName, args ->
            when (methodName) {
                "getAll" -> getAll()
                "getArchived" -> getArchived()
                "getDefault" -> getDefault()
                "getDefaults" -> defaultMasks
                "getById" -> getById((args[0] as Number).toInt())
                "insert" -> insert(args[0] as AiMask)
                "insertDefaultIfMissing" -> {
                    insertDefaultIfMissing(
                        name = args[0] as String,
                        systemPrompt = args[1] as String,
                        groupName = args[2] as String,
                        updatedAt = (args[3] as Number).toLong(),
                        lastUsedAt = (args[4] as Number).toLong()
                    )
                    Unit
                }
                "deleteById" -> {
                    deleteById((args[0] as Number).toInt())
                    Unit
                }
                "deleteByIds" -> {
                    @Suppress("UNCHECKED_CAST")
                    deleteByIds(args[0] as List<Int>)
                    Unit
                }
                else -> unsupportedCall("RecordingAiMaskDao", methodName)
            }
        }

        val defaultMasks: List<AiMask>
            get() = synchronized(lock) {
                masks.filter { it.isDefault }.sortedBy { it.id }
            }

        private fun getAll(): List<AiMask> {
            return synchronized(lock) {
                masks.filter { !it.isArchived }
                    .sortedWith(compareByDescending<AiMask> { it.isDefault }.thenBy { it.groupName }.thenByDescending { it.lastUsedAt }.thenByDescending { it.updatedAt })
            }
        }

        private fun getArchived(): List<AiMask> {
            return synchronized(lock) {
                masks.filter { it.isArchived }
                    .sortedByDescending { it.updatedAt }
            }
        }

        private fun getDefault(): AiMask? {
            val result = defaultMasks.firstOrNull()
            if (result == null) {
                val barrier = emptyDefaultReadBarrier
                val currentRead = emptyDefaultReadCount.incrementAndGet()
                if (barrier != null && currentRead <= barrier.parties) {
                    barrier.await(1, TimeUnit.SECONDS)
                }
            }
            return result
        }

        private fun getById(id: Int): AiMask? {
            return synchronized(lock) {
                masks.firstOrNull { it.id == id }
            }
        }

        private fun insert(mask: AiMask): Long {
            return synchronized(lock) {
                val assignedId = if (mask.id == 0) nextId.getAndIncrement() else mask.id
                masks.removeAll { it.id == assignedId }
                masks.add(mask.copy(id = assignedId))
                assignedId.toLong()
            }
        }

        private fun insertDefaultIfMissing(
            name: String,
            systemPrompt: String,
            groupName: String,
            updatedAt: Long,
            lastUsedAt: Long
        ) {
            val shouldInsert = synchronized(lock) {
                masks.none { it.isDefault }
            }
            val barrier = insertDefaultIfMissingBarrier
            val currentInsert = insertDefaultIfMissingCount.incrementAndGet()
            if (shouldInsert && barrier != null && currentInsert <= barrier.parties) {
                try {
                    barrier.await(1, TimeUnit.SECONDS)
                } catch (_: BrokenBarrierException) {
                    // 串行化后只会有一个调用进入这里，直接继续即可。
                } catch (_: TimeoutException) {
                    // 串行化后不会形成并发写入，这里不把测试桩超时当成失败。
                }
            }
            if (!shouldInsert) return
            synchronized(lock) {
                masks.add(
                    AiMask(
                        id = nextId.getAndIncrement(),
                        name = name,
                        systemPrompt = systemPrompt,
                        groupName = groupName,
                        isDefault = true,
                        isArchived = false,
                        updatedAt = updatedAt,
                        lastUsedAt = lastUsedAt
                    )
                )
            }
        }

        private fun deleteById(id: Int) {
            synchronized(lock) {
                masks.removeAll { it.id == id }
            }
        }

        private fun deleteByIds(ids: List<Int>) {
            synchronized(lock) {
                masks.removeAll { it.id in ids }
            }
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

        private fun unsupportedCall(targetName: String, methodName: String): Nothing {
            error("$targetName.$methodName is not expected in this test")
        }
    }

    private class RecordingChatRoomDao(initialChats: List<ChatRoom>) {
        private val lock = Any()
        private val chats = initialChats.toMutableList()

        val dao: ChatRoomDao = createProxy<ChatRoomDao>("RecordingChatRoomDao") { methodName, args ->
            when (methodName) {
                "clearMaskIdByMaskIds" -> {
                    @Suppress("UNCHECKED_CAST")
                    clearMaskIdByMaskIds(args[0] as List<Int>)
                    Unit
                }
                else -> unsupportedCall("RecordingChatRoomDao", methodName)
            }
        }

        val currentChats: List<ChatRoom>
            get() = synchronized(lock) { chats.toList() }

        private fun clearMaskIdByMaskIds(maskIds: List<Int>) {
            synchronized(lock) {
                chats.replaceAll { chat ->
                    if (chat.maskId in maskIds) {
                        chat.copy(maskId = null)
                    } else {
                        chat
                    }
                }
            }
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

        private fun unsupportedCall(targetName: String, methodName: String): Nothing {
            error("$targetName.$methodName is not expected in this test")
        }
    }

    private class ImmediateTransactionRunner : DatabaseTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
}
