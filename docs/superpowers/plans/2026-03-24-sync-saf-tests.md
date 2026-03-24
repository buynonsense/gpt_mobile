# Sync SAF And Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为设置中的数据同步页面补上系统文件导入导出，并为同步模块增加稳定的纯单元测试。

**Architecture:** 保持现有 `SyncViewModel + SyncScreen + SyncRepository` 结构不变，只在页面层接入 Android SAF 的 `CreateDocument` / `OpenDocument`，把 URI 读写封装为最小的辅助函数。测试层分两层：主机单元测试覆盖纯 Kotlin 行为（备份密码加解密、WebDAV XML 解析、备份 DTO 映射），UI/SAF 行为采用最小 instrumented test 或手动验证补位，因为 `ActivityResultLauncher` 与 `ContentResolver` 真正交互不适合放进主机单元测试。

**Tech Stack:** Kotlin、Jetpack Compose、Activity Result API、Kotlinx Serialization、JUnit4。

---

### Task 1: 为备份密码加解密补单元测试

**Files:**
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/PasswordCryptoHelperTest.kt`
- Create: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/PasswordCryptoHelper.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupCryptoManager.kt`

- [ ] **Step 1: 写失败测试，先覆盖密码加密成功后可被正确解密**

```kotlin
@Test
fun encryptAndDecryptBackup_returnsOriginalPlainText() {
    val encrypted = PasswordCryptoHelper.encryptForBackup("hello", "secret")

    val plain = PasswordCryptoHelper.decryptBackup(
        cipherText = encrypted.cipherText,
        password = "secret",
        salt = encrypted.salt,
        iv = encrypted.iv,
        iterations = encrypted.iterations
    )

    assertEquals("hello", plain)
}
```

- [ ] **Step 2: 运行单测确认红灯**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.PasswordCryptoHelperTest"`
Expected: FAIL，提示辅助对象或方法不存在。

- [ ] **Step 3: 提取纯 Kotlin 的密码加解密辅助对象，避免测试依赖 Android Keystore**

```kotlin
object PasswordCryptoHelper {
    fun encryptForBackup(plainText: String, password: String): PasswordEncryptionResult = ...
    fun decryptBackup(...): String = ...
}
```

- [ ] **Step 4: 再补“错误密码必须失败”的失败测试**

```kotlin
@Test(expected = AEADBadTagException::class)
fun decryptBackup_withWrongPassword_throws() {
    ...
}
```

- [ ] **Step 5: 运行单测确认红灯，然后仅做最小实现让测试变绿**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.PasswordCryptoHelperTest"`
Expected: PASS


### Task 2: 为 WebDAV XML 解析补单元测试

**Files:**
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/WebDavXmlParserTest.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/WebDavXmlParser.kt`（仅在测试暴露缺陷时修改）

- [ ] **Step 1: 写失败测试，验证能解析 displayname、size、etag、modifiedAt，并使用固定 XML fixture 保持顺序稳定**

```kotlin
@Test
fun parse_returnsRemoteFilesFromPropfindXml() {
    val xml = """...MULTISTATUS XML...""".trimIndent()

    val files = WebDavXmlParser.parse(xml).sortedBy { it.name }

    assertEquals(2, files.size)
    assertEquals("backup-1.json", files[0].name)
}
```

- [ ] **Step 2: 运行单测确认红灯**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.WebDavXmlParserTest"`
Expected: FAIL，如果当前顺序或字段不符合预期。

- [ ] **Step 3: 只做最小修复，确保解析结果稳定**

- [ ] **Step 4: 再补一个失败测试，验证缺少 displayname 时会回退到 href 文件名，且目录自身节点不会影响断言**

```kotlin
@Test
fun parse_withoutDisplayName_fallsBackToHrefName() { ... }
```

- [ ] **Step 5: 再跑单测确认全部通过**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.WebDavXmlParserTest"`
Expected: PASS


### Task 3: 为备份 DTO 映射补单元测试

**Files:**
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/model/BackupModelsTest.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/model/BackupModels.kt`（仅在测试暴露缺陷时修改）

- [ ] **Step 1: 写失败测试，验证 Platform -> BackupPlatform -> Platform 可往返**

```kotlin
@Test
fun platformMapping_roundTripsWithoutLosingValues() { ... }
```

- [ ] **Step 2: 写失败测试，验证 ChatRoom / Message / AiMask 的关键字段可往返**

```kotlin
@Test
fun entityMappings_roundTripCoreFields() { ... }
```

- [ ] **Step 3: 运行单测确认红灯**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.model.BackupModelsTest"`
Expected: FAIL，如果映射有丢字段或枚举恢复问题。

- [ ] **Step 4: 做最小实现修复，保持映射函数单一职责**

- [ ] **Step 5: 再跑测试确认变绿**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.model.BackupModelsTest"`
Expected: PASS


### Task 4: 为同步页面接入 SAF 导出文件

**Files:**
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncViewModel.kt`
- Create: `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncScreenTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 写失败的 UI 验证用例，至少覆盖“没有备份内容时点击保存到文件会显示提示”**

```kotlin
@Test
fun saveToFile_withoutBackupContent_showsHint() { ... }
```

- [ ] **Step 2: 运行该测试确认红灯**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.setting.SyncScreenTest`
Expected: FAIL，提示按钮或文案尚不存在。

- [ ] **Step 3: 实现页面层 launcher，不新增 ViewModel 依赖，只在页面持有 launcher**

```kotlin
val exportLauncher = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri -> ... }
```

- [ ] **Step 4: 先让页面出现“保存到文件”按钮，点击时在没有备份内容的情况下用状态消息提示，不触发写文件**

- [ ] **Step 5: 运行 instrumented test 确认变绿**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.setting.SyncScreenTest`
Expected: PASS

- [ ] **Step 6: 用最小实现把 `uiState.localBackupJson` 写入 `contentResolver.openOutputStream(uri)`**

```kotlin
context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
    writer.write(uiState.localBackupJson.orEmpty())
}
```

- [ ] **Step 7: 为成功导出补一条验证步骤：写文件成功后必须显示成功提示，失败时显示错误提示**

Run: `./gradlew :app:assembleDebug`
Manual checks:
- 生成备份后点击“保存到文件”可成功导出 `.json`
- 成功保存后界面显示“备份已保存到文件”
- 写入失败或 URI 无效时界面显示错误提示

- [ ] **Step 8: 明确页面失败反馈方案：新增 `syncViewModel.showError(...)`，不要在页面里临时拼接错误状态**


### Task 5: 为同步页面接入 SAF 导入文件

**Files:**
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncScreen.kt`
- Modify: `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncScreenTest.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 写失败的 UI 验证用例，至少覆盖“导入按钮存在且在选择文件后会把内容填入恢复区”**

如果自动化难以稳定模拟文件选择，需在测试里只断言按钮与恢复区联动入口存在，并在计划执行时补手动验证记录。

- [ ] **Step 2: 在页面新增 `OpenDocument()` launcher**

```kotlin
val importLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri -> ... }
```

- [ ] **Step 3: 先添加“从文件导入”按钮，点击后选择 `application/json`**

- [ ] **Step 4: 失败路径统一走 `syncViewModel.showError(...)`：URI 为空、读取为空、读取异常都显示错误提示**

- [ ] **Step 5: 读取文件内容后调用 `syncViewModel.updateImportedBackupJson(content)` 与 `syncViewModel.loadImportedSummary()`**

- [ ] **Step 6: 保留当前手动粘贴输入框，确保两种入口兼容**

- [ ] **Step 7: 在真实设备或模拟器上手动验证 4 条路径并记录结果**

Run: `./gradlew :app:assembleDebug`
Manual checks:
- 生成备份后可以保存为 `.json`
- 空备份时点击保存会提示错误
- 选择本地 `.json` 后恢复区被填充
- 非法或空文件会提示错误


### Task 6: 统一文案与状态提示

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncViewModel.kt`

- [ ] **Step 1: 增加 SAF 相关文案**

```xml
<string name="save_backup_to_file">保存到文件</string>
<string name="import_backup_from_file">从文件导入</string>
<string name="backup_saved_to_file">备份已保存到文件</string>
```

- [ ] **Step 2: 清理过时提示，例如“首版暂未接入系统文件选择器”**

- [ ] **Step 3: 为 `SyncViewModel` 增加公开 `showError(message: String)`，统一文件导入导出失败提示**

- [ ] **Step 4: 确保状态提示不和现有“复制备份内容”逻辑冲突**


### Task 7: 跑完整验证

**Files:**
- Test: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/PasswordCryptoHelperTest.kt`
- Test: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/WebDavXmlParserTest.kt`
- Test: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/model/BackupModelsTest.kt`

- [ ] **Step 1: 依次跑新增单测，确认每组测试单独通过**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.PasswordCryptoHelperTest"`
Expected: PASS

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.WebDavXmlParserTest"`
Expected: PASS

Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.model.BackupModelsTest"`
Expected: PASS

- [ ] **Step 2: 跑完整单元测试回归**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS

- [ ] **Step 3: 跑最小 instrumented test，验证同步页面的按钮和基础提示逻辑**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.setting.SyncScreenTest`
Expected: PASS（若当前环境无设备，则明确记录未执行并给出人工验证结果）

- [ ] **Step 4: 跑调试构建确认 Compose 与资源改动未破坏打包**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupCryptoManager.kt \
  app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/PasswordCryptoHelper.kt \
  app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/WebDavXmlParser.kt \
  app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/sync/model/BackupModels.kt \
  app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncScreen.kt \
  app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncViewModel.kt \
  app/src/main/res/values/strings.xml \
  app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncScreenTest.kt \
  app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/PasswordCryptoHelperTest.kt \
  app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/WebDavXmlParserTest.kt \
  app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/model/BackupModelsTest.kt \
  docs/superpowers/plans/2026-03-24-sync-saf-tests.md
git commit -m "feat: 补充同步文件导入导出与单元测试"
```
