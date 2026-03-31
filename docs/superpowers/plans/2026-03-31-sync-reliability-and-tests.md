# Sync Reliability And Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为数据同步模块补齐仓库层可靠性测试，并只修复测试明确暴露的问题。

**Architecture:** 优先用主机单元测试覆盖 `BackupRepositoryImpl` 的导出/解析行为和 `SyncRepositoryImpl` 的冲突检测/配置归一化行为。对 `BackupRepositoryImpl` 的恢复写回路径，如果 `ChatDatabase.withTransaction` 在 JVM 测试里不可稳测，则下沉为最小 `androidTest`；对 `SyncRepositoryImpl` 中依赖本地加密的配置保存路径，则通过抽取极小加密接口来解除测试耦合。

**Tech Stack:** Kotlin、JUnit4、Kotlinx Serialization、Room、Hilt、Android instrumented test。

---

## File Map

- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplTest.kt`
  作用：覆盖备份导出、坏 JSON、错误密码、schema 错误等主机单测
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/SyncRepositoryImplTest.kt`
  作用：覆盖配置归一化、冲突检测、上传/下载委托等主机单测
- Create: `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplInstrumentedTest.kt`（仅在恢复路径无法稳定做 JVM 测试时）
  作用：用最小 Android 环境覆盖恢复写回行为
- Create: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/LocalSecretCipher.kt`（仅在 `SyncRepositoryImpl` 的保存/读取配置测试确实需要时）
  作用：隔离本地加密实现，让 `SyncRepositoryImpl` 可做主机单测
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImpl.kt`
  作用：仅在测试暴露问题时做最小修复
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/SyncRepositoryImpl.kt`
  作用：仅在测试暴露问题时做最小修复，或接入最小加密 seam
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupCryptoManager.kt`
  作用：若引入 `LocalSecretCipher`，则实现接口并保留现有行为
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/di/SyncModule.kt`
  作用：若引入 `LocalSecretCipher`，提供接口绑定

### Task 1: 覆盖 `BackupRepositoryImpl` 导出与解析主机单测

**Files:**
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplTest.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImpl.kt`（仅测试暴露问题时）

- [ ] **Step 1: 写失败测试，验证导出摘要计数和 `containsSecrets`**

```kotlin
@Test
fun exportBackup_buildsAccurateSummary() = runTest {
    val repository = createRepository(
        platforms = listOf(platform(token = "secret-token")),
        chatRooms = listOf(chatRoom(id = 1), chatRoom(id = 2)),
        messages = listOf(message(id = 1)),
        masks = listOf(mask(id = 1), mask(id = 2), mask(id = 3))
    )

    val backup = repository.exportBackup("pw")

    assertEquals(2, backup.summary.chatRoomCount)
    assertEquals(1, backup.summary.messageCount)
    assertEquals(3, backup.summary.aiMaskCount)
    assertTrue(backup.summary.containsSecrets)
}
```

- [ ] **Step 2: 运行测试确认红灯**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImplTest.exportBackup_buildsAccurateSummary"`
Expected: FAIL，提示测试文件/辅助结构尚不存在。

- [ ] **Step 3: 继续补失败测试，验证加密元信息合同**

```kotlin
@Test
fun exportBackup_populatesExpectedEncryptionMetadata() = runTest {
    val backup = createRepository().exportBackup("pw")

    assertTrue(backup.encryption.enabled)
    assertEquals("AES/GCM/NoPadding", backup.encryption.algorithm)
    assertEquals("PBKDF2WithHmacSHA256", backup.encryption.kdf)
    assertTrue(backup.encryption.salt.isNotBlank())
    assertTrue(backup.encryption.iv.isNotBlank())
    assertTrue(backup.encryption.iterations > 0)
}
```

- [ ] **Step 4: 继续补失败测试，分别验证非法 JSON、缺少关键字段、schema 错误**

```kotlin
@Test
fun parseBackupFile_withMalformedJson_throws() {
    assertFails {
        repository.parseBackupFile("not-json")
    }
}

@Test
fun parseBackupFile_withMissingRequiredField_throws() {
    assertFails {
        repository.parseBackupFile("""{"schemaVersion":1}""")
    }
}

@Test
fun parseBackupFile_withUnsupportedSchema_throws() {
    val error = assertFailsWith<IllegalArgumentException> {
        repository.parseBackupFile(fileContentWithSchema(99))
    }

    assertTrue(error.message.orEmpty().contains("Unsupported backup schema version"))
}
```

- [ ] **Step 5: 继续补失败测试，验证错误密码恢复失败**

```kotlin
@Test
fun restoreBackup_withWrongPassword_throwsInvalidBackupPassword() = runTest {
    val exported = repository.exportBackup("correct")

    val error = assertFailsWith<IllegalArgumentException> {
        repository.restoreBackup(exported.toJson(), "wrong")
    }

    assertEquals("Invalid backup password", error.message)
}
```

- [ ] **Step 6: 运行整组测试确认红灯**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImplTest"`
Expected: FAIL，且失败原因与缺失测试支撑/实现问题一致。

- [ ] **Step 7: 只做最小实现修复，让导出/解析/错误路径测试转绿**

允许的最小修复示例：

```kotlin
require(backupFile.schemaVersion == SCHEMA_VERSION) {
    "Unsupported backup schema version: ${backupFile.schemaVersion}"
}
```

- [ ] **Step 8: 重新运行整组测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImplTest"`
Expected: PASS

- [ ] **Step 9: 记录 checkpoint**

如果用户要求提交，只提交本任务相关文件；否则至少执行 `git diff -- app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplTest.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImpl.kt` 自检范围。


### Task 2: 锁定 `BackupRepositoryImpl` 恢复写回行为

**Files:**
- Modify: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplTest.kt`
- Create: `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplInstrumentedTest.kt`（如果 JVM 方案不可行）
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImpl.kt`（仅测试暴露问题时）

- [ ] **Step 1: 先尝试写失败的 JVM 测试，验证恢复成功后会清空并重写本地数据**

```kotlin
@Test
fun restoreBackup_replacesLocalDataAndUpdatesSettings() = runTest {
    val previousChat = chatRoom(id = 7, title = "old")
    fakeChatRoomDao.seed(previousChat)
    val repository = createRepository(
        chatRooms = listOf(chatRoom(id = 1, title = "new")),
        messages = listOf(message(id = 11, chatId = 1)),
        masks = listOf(mask(id = 21, name = "mask")),
        platforms = listOf(platform(token = "secret-token"))
    )
    val backupJson = repository.exportBackup("pw").toJson()

    repository.restoreBackup(backupJson, "pw")

    assertEquals(listOf(chatRoom(id = 1, title = "new")), fakeChatRoomDao.currentData)
    assertEquals(listOf(platform(token = "secret-token")), fakeSettingRepository.updatedPlatforms)
}
```

- [ ] **Step 2: 运行测试确认是“业务红灯”还是被 `withTransaction` 阻塞**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImplTest.restoreBackup_replacesLocalDataAndUpdatesSettings"`
Expected: 若因 `ChatDatabase.withTransaction` 无法稳定运行而阻塞，则进入 Step 3；否则保留 JVM 路线。

- [ ] **Step 3: 如果 JVM 路线被事务阻塞，改写为最小 instrumented test**

```kotlin
@Test
fun restoreBackup_replacesLocalDataAndUpdatesSettings() = runTest {
    val database = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java).build()
    val targetPlatforms = listOf(platform(token = "secret-token"))
    val repository = createRepository(
        database = database,
        settingRepository = fakeSettingRepository,
        chatRooms = listOf(chatRoom(id = 1, title = "new")),
        messages = listOf(message(id = 11, chatId = 1)),
        masks = listOf(mask(id = 21, name = "mask")),
        platforms = targetPlatforms
    )
    val backupJson = repository.exportBackup("pw").toJson()

    repository.restoreBackup(backupJson, "pw")

    assertEquals(listOf(chatRoom(id = 1, title = "new")), database.chatRoomDao().getAll())
    assertEquals(targetPlatforms, fakeSettingRepository.updatedPlatforms)
}
```

- [ ] **Step 4: 再补一个失败测试，锁定“设置更新阶段抛错时的当前行为”**

```kotlin
@Test
fun restoreBackup_whenSettingsUpdateFails_locksCurrentPartialFailureBehavior() = runTest {
    val previousChat = chatRoom(id = 7, title = "old")
    fakeChatRoomDao.seed(previousChat)
    val repository = createRepository(
        chatRooms = listOf(chatRoom(id = 1, title = "new")),
        messages = listOf(message(id = 11, chatId = 1)),
        masks = listOf(mask(id = 21, name = "mask")),
        platforms = listOf(platform(token = "secret-token"))
    )
    val backupJson = repository.exportBackup("pw").toJson()
    fakeSettingRepository.failOnUpdateThemes = true

    assertFailsWith<IllegalStateException> {
        repository.restoreBackup(backupJson, "pw")
    }

    assertEquals(listOf(previousChat), fakeChatRoomDao.currentData)
    assertEquals(listOf(platform(token = "secret-token")), fakeSettingRepository.updatedPlatforms)
    assertNull(fakeSettingRepository.updatedThemes)
}
```

- [ ] **Step 5: 运行对应测试确认红灯**

Run one of:
- `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImplTest"`
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImplInstrumentedTest`

Expected: FAIL，且失败原因对应恢复路径当前真实问题。

- [ ] **Step 6: 只修测试暴露的问题，不改变恢复总体行为边界**

- [ ] **Step 7: 重新运行对应测试确认通过**

Run one of:
- `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImplTest"`
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImplInstrumentedTest`

Expected: PASS

- [ ] **Step 8: 记录 checkpoint**

如果用户要求提交，只提交本任务相关文件；否则至少执行以下之一自检范围：

- JVM 路线：`git diff -- app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplTest.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImpl.kt`
- instrumented 路线：`git diff -- app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplTest.kt app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplInstrumentedTest.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImpl.kt`


### Task 3: 为 `SyncRepositoryImpl` 补配置归一化与冲突检测测试

**Files:**
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/SyncRepositoryImplTest.kt`
- Create: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/LocalSecretCipher.kt`（如需要）
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/SyncRepositoryImpl.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupCryptoManager.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/di/SyncModule.kt`

- [ ] **Step 1: 写失败测试，验证 `testWebDavConnection` 会归一化地址与路径**

```kotlin
@Test
fun testWebDavConnection_normalizesBaseUrlAndRemotePath() = runTest {
    repository.testWebDavConnection(
        baseUrl = "https://dav.example.com///",
        username = "user",
        remotePath = "/backup/path/",
        password = "pw"
    )

    assertEquals("https://dav.example.com", fakeWebDavRepository.testedConfig?.baseUrl)
    assertEquals("backup/path", fakeWebDavRepository.testedConfig?.remotePath)
}
```

- [ ] **Step 2: 写失败测试，验证 `getWebDavConfig` 与 `clearWebDavConfig` 的委托行为**

```kotlin
@Test
fun getWebDavConfig_returnsStoredConfig() = runTest {
    val config = WebDavConfig(
        baseUrl = "https://dav.example.com",
        username = "user",
        remotePath = "backup"
    )
    fakeSettingRepository.savedConfig = config

    assertEquals(config, repository.getWebDavConfig())
}

@Test
fun clearWebDavConfig_clearsStoredConfig() = runTest {
    fakeSettingRepository.savedConfig = WebDavConfig(
        baseUrl = "https://dav.example.com",
        username = "user",
        remotePath = "backup"
    )

    repository.clearWebDavConfig()

    assertNull(fakeSettingRepository.savedConfig)
}
```

- [ ] **Step 3: 写失败测试，验证没有配置时报错**

```kotlin
@Test
fun listRemoteBackups_withoutConfig_throws() = runTest {
    fakeSettingRepository.savedConfig = null

    assertFailsWith<IllegalStateException> {
        repository.listRemoteBackups("pw")
    }
}

@Test
fun uploadBackup_withoutConfig_throws() = runTest {
    fakeSettingRepository.savedConfig = null

    assertFailsWith<IllegalStateException> {
        repository.uploadBackup(password = "pw", overwrite = true)
    }
}

@Test
fun downloadRemoteBackup_withoutConfig_throws() = runTest {
    fakeSettingRepository.savedConfig = null

    assertFailsWith<IllegalStateException> {
        repository.downloadRemoteBackup(password = "pw", remoteFileName = "remote.json")
    }
}
```

- [ ] **Step 4: 写失败测试，验证没有远端备份时不报冲突**

```kotlin
@Test
fun detectUploadConflict_withoutRemoteBackups_returnsNull() = runTest {
    fakeWebDavRepository.remoteFiles = emptyList()

    assertNull(repository.detectUploadConflict("pw"))
}
```

- [ ] **Step 5: 写失败测试，验证远端较新时返回冲突对象**

```kotlin
@Test
fun detectUploadConflict_withNewerRemoteBackup_returnsConflict() = runTest {
    val conflict = repository.detectUploadConflict("pw")

    assertEquals("remote.json", conflict?.remoteFileName)
    assertEquals(1, conflict?.localSummary?.chatRoomCount)
    assertEquals(2, conflict?.remoteSummary?.chatRoomCount)
    assertEquals(100L, conflict?.localExportedAt)
    assertEquals(200L, conflict?.remoteExportedAt)
}
```

- [ ] **Step 6: 写失败测试，验证远端不较新时不报冲突**

```kotlin
@Test
fun detectUploadConflict_withOlderRemoteBackup_returnsNull() = runTest {
    fakeWebDavRepository.downloadedContent = oldRemoteBackupJson

    assertNull(repository.detectUploadConflict("pw"))
}
```

- [ ] **Step 7: 写失败测试，验证远端损坏备份时明确失败**

```kotlin
@Test
fun detectUploadConflict_withCorruptedRemoteBackup_throws() = runTest {
    fakeWebDavRepository.downloadedContent = "not-json"

    assertFails {
        repository.detectUploadConflict("pw")
    }
}

@Test
fun detectUploadConflict_withUnsupportedRemoteSchema_throws() = runTest {
    fakeWebDavRepository.downloadedContent = fileContentWithSchema(99)

    assertFailsWith<IllegalArgumentException> {
        repository.detectUploadConflict("pw")
    }
}
```

- [ ] **Step 8: 写失败测试，验证 `uploadBackup(overwrite = false)` 遇冲突时失败**

```kotlin
@Test
fun uploadBackup_withoutOverwrite_whenConflictExists_throws() = runTest {
    assertFailsWith<IllegalStateException> {
        repository.uploadBackup(password = "pw", overwrite = false)
    }
}
```

- [ ] **Step 9: 写失败测试，验证 `uploadBackup(overwrite = true)` 会上传**

```kotlin
@Test
fun uploadBackup_withOverwrite_uploadsBackup() = runTest {
    repository.uploadBackup(password = "pw", overwrite = true)

    assertEquals(1, fakeWebDavRepository.uploadCalls.size)
}
```

- [ ] **Step 10: 写失败测试，验证 `downloadRemoteBackup` 会直接转发远端内容**

```kotlin
@Test
fun downloadRemoteBackup_returnsRemoteContent() = runTest {
    fakeWebDavRepository.downloadedContent = remoteBackupJson

    assertEquals(remoteBackupJson, repository.downloadRemoteBackup("pw", "remote.json"))
}
```

- [ ] **Step 11: 写失败测试，验证保存配置时的归一化结果**

```kotlin
@Test
fun saveWebDavConfig_persistsNormalizedConfig() = runTest {
    repository.saveWebDavConfig("https://dav.example.com/", "user", "/path/", "pw")

    assertEquals("https://dav.example.com", fakeSettingRepository.savedConfig?.baseUrl)
    assertEquals("path", fakeSettingRepository.savedConfig?.remotePath)
}
```

- [ ] **Step 12: 运行整组测试确认红灯**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.SyncRepositoryImplTest"`
Expected: FAIL，可能暴露 `BackupCryptoManager` 本地加密依赖导致难测。

- [ ] **Step 13: 如果 `saveWebDavConfig` 因本地加密依赖难测，抽取最小接口**

```kotlin
interface LocalSecretCipher {
    fun encryptForLocalStorage(plainText: String): BackupCryptoManager.LocalEncryptionResult
    fun decryptFromLocalStorage(cipherText: String, iv: String): String
}
```

- [ ] **Step 14: 让 `BackupCryptoManager` 实现该接口，并通过 Hilt 绑定给 `SyncRepositoryImpl`**

- [ ] **Step 15: 只做最小实现修复，让整组测试转绿**

- [ ] **Step 16: 重新运行整组测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.SyncRepositoryImplTest"`
Expected: PASS

- [ ] **Step 17: 记录 checkpoint**

如果用户要求提交，只提交本任务相关文件；否则至少执行 `git diff -- app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/SyncRepositoryImplTest.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/SyncRepositoryImpl.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupCryptoManager.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/LocalSecretCipher.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/di/SyncModule.kt` 自检范围。


### Task 4: 跑完整验证并收口

**Files:**
- Test: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplTest.kt`
- Test: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/SyncRepositoryImplTest.kt`
- Test: `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplInstrumentedTest.kt`（若创建）

- [ ] **Step 1: 依次跑仓库层新增测试**

Run:
- `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImplTest"`
- `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.SyncRepositoryImplTest"`

Expected: PASS

- [ ] **Step 2: 如果新增了恢复路径 instrumented test，则单独跑它**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImplInstrumentedTest`
Expected: PASS

- [ ] **Step 3: 跑完整单元测试回归**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS

- [ ] **Step 4: 跑调试构建**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 若改动触及同步页状态文案，再补跑现有同步页 UI 测试**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.setting.SyncScreenTest`
Expected: PASS

- [ ] **Step 6: 自检是否只修复了测试暴露的问题**

Checklist:
- 没有新增自动同步
- 没有改动备份格式主版本号
- 没有顺手重构同步架构
- 改动集中在测试与最小实现修复

- [ ] **Step 7: 收口最终 diff，确保没有超出本轮范围的改动**

Run: `git diff -- app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/data/sync app/src/main/kotlin/dev/chungjungsoo/gptmobile/di/SyncModule.kt`
Expected: diff 只包含同步仓库测试、必要的最小实现修复，以及可选的最小事务/加密 seam。
