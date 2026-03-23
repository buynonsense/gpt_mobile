package dev.chungjungsoo.gptmobile.data.sync

import androidx.room.withTransaction
import dev.chungjungsoo.gptmobile.data.database.ChatDatabase
import dev.chungjungsoo.gptmobile.data.database.dao.AiMaskDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.sync.model.BackupDatabase
import dev.chungjungsoo.gptmobile.data.sync.model.BackupEncryption
import dev.chungjungsoo.gptmobile.data.sync.model.BackupFile
import dev.chungjungsoo.gptmobile.data.sync.model.BackupPayload
import dev.chungjungsoo.gptmobile.data.sync.model.BackupSettings
import dev.chungjungsoo.gptmobile.data.sync.model.BackupSummary
import dev.chungjungsoo.gptmobile.data.sync.model.toBackupModel
import dev.chungjungsoo.gptmobile.data.sync.model.toAiMask
import dev.chungjungsoo.gptmobile.data.sync.model.toChatRoom
import dev.chungjungsoo.gptmobile.data.sync.model.toMessage
import dev.chungjungsoo.gptmobile.data.sync.model.toPlatform
import dev.chungjungsoo.gptmobile.data.sync.model.toStreamingStyle
import dev.chungjungsoo.gptmobile.data.sync.model.toThemeSetting
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val chatDatabase: ChatDatabase,
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val aiMaskDao: AiMaskDao,
    private val settingRepository: SettingRepository,
    private val cryptoManager: BackupCryptoManager
) : BackupRepository {

    override suspend fun exportBackup(password: String): BackupFile {
        val platforms = settingRepository.fetchPlatforms()
        val themes = settingRepository.fetchThemes()
        val streamingStyle = settingRepository.fetchStreamingStyle()
        val chatRooms = chatRoomDao.getAll()
        val messages = messageDao.getAll()
        val masks = aiMaskDao.getAllIncludingArchived()

        val payload = BackupPayload(
            settings = BackupSettings(
                platforms = platforms.map { it.toBackupModel() },
                theme = themes.toBackupModel(),
                streamingStyle = streamingStyle.toBackupModel()
            ),
            database = BackupDatabase(
                chatRooms = chatRooms.map { it.toBackupModel() },
                messages = messages.map { it.toBackupModel() },
                aiMasks = masks.map { it.toBackupModel() }
            )
        )
        val plainPayload = json.encodeToString(BackupPayload.serializer(), payload)
        val encrypted = cryptoManager.encryptForBackup(plainPayload, password)

        return BackupFile(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = APP_VERSION,
            backupType = FULL_BACKUP,
            summary = BackupSummary(
                chatRoomCount = chatRooms.size,
                messageCount = messages.size,
                aiMaskCount = masks.size,
                containsSecrets = platforms.any { !it.token.isNullOrBlank() }
            ),
            encryption = BackupEncryption(
                enabled = true,
                algorithm = AES_ALGORITHM,
                kdf = KDF_ALGORITHM,
                iterations = encrypted.iterations,
                salt = encrypted.salt,
                iv = encrypted.iv
            ),
            payload = encrypted.cipherText
        )
    }

    override suspend fun restoreBackup(fileContent: String, password: String) {
        val backupFile = parseBackupFile(fileContent)
        require(backupFile.encryption.enabled) { "Backup encryption is required" }
        val plainPayload = cryptoManager.decryptBackup(
            cipherText = backupFile.payload,
            password = password,
            salt = backupFile.encryption.salt,
            iv = backupFile.encryption.iv,
            iterations = backupFile.encryption.iterations
        )
        val payload = json.decodeFromString(BackupPayload.serializer(), plainPayload)

        chatDatabase.withTransaction {
            messageDao.deleteAll()
            chatRoomDao.deleteAll()
            aiMaskDao.deleteAll()

            aiMaskDao.insertAll(payload.database.aiMasks.map { it.toAiMask() })
            chatRoomDao.insertAll(payload.database.chatRooms.map { it.toChatRoom() })
            messageDao.insertAll(payload.database.messages.map { it.toMessage() })

            settingRepository.updatePlatforms(payload.settings.platforms.map { it.toPlatform() })
            settingRepository.updateThemes(payload.settings.theme.toThemeSetting())
            settingRepository.updateStreamingStyle(payload.settings.streamingStyle.toStreamingStyle())
        }
    }

    override suspend fun parseBackupFile(fileContent: String): BackupFile {
        val backupFile = json.decodeFromString(BackupFile.serializer(), fileContent)
        require(backupFile.schemaVersion == SCHEMA_VERSION) {
            "Unsupported backup schema version: ${backupFile.schemaVersion}"
        }

        return backupFile
    }

    override suspend fun readSummary(fileContent: String): BackupSummary = parseBackupFile(fileContent).summary

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val FULL_BACKUP = "full"
        private const val APP_VERSION = "0.9.6"
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    }
}
