# 数据同步可靠性与测试补强设计

## 背景

当前分支已经完成以下能力：

- 设置页新增数据同步入口
- 本地全量备份与恢复
- 备份内容使用用户输入的备份密码加密，API key 包含在备份内
- WebDAV 云端全量备份上传、下载与冲突检测
- 冲突时支持两条手动路径：覆盖云端，或先下载云端备份到恢复区再决定是否恢复到本地

现阶段主链路已可用，但可靠性仍主要依赖人工验证。同步层目前最值得补强的是“测试覆盖 + 只修测试暴露的问题”，而不是继续叠加新功能。

## 目标

本轮目标只做“可靠性与测试补强”，不扩展功能边界：

1. 为同步核心逻辑建立稳定、可重复执行的测试覆盖
2. 用测试驱动方式暴露现有实现里的真实问题
3. 只对测试明确暴露的问题做最小修复
4. 保持当前交互和架构边界基本不变

## 非目标

本轮明确不做：

- 不新增云协议
- 不实现自动同步、后台同步、定时任务
- 不做同步模块大重构
- 不调整现有备份文件格式的主版本号，除非测试证明当前格式存在阻塞性问题
- 不扩展新的 UI 功能，只在测试暴露用户可见错误时补必要提示
- 不在本轮为 Android Keystore 本地密码存储专门新增主机单元测试；除非测试驱动出必须抽 seam 的问题，否则不扩张到这一层

## 当前风险点

### 1. `BackupRepositoryImpl` 缺少端到端行为测试

目前导出与恢复依赖多个 DAO、`SettingRepository`、序列化与加解密协作，但还没有围绕以下场景的自动化验证：

- 导出摘要计数是否准确
- `containsSecrets` 是否按 token 状态正确计算
- 错误备份密码是否稳定失败
- 损坏 JSON 或不支持 schema 是否稳定失败
- 恢复时是否真的按预期覆盖数据库和设置

恢复流程还有一个需要显式锁定的风险：Room 数据写入位于 `withTransaction` 中，但 `SettingRepository` 更新并不真正受 Room 事务保护。如果恢复途中在设置更新阶段失败，理论上可能留下“数据库已恢复、设置未完全恢复”的部分成功状态。本轮先用测试把当前行为写清楚，再决定是否做最小修复。

### 2. `SyncRepositoryImpl` 的冲突检测逻辑可用但测试不足

当前冲突判断依赖“远端最新备份是否比本地新”，但没有系统验证：

- 无远端备份时是否不报冲突
- 远端较新时是否稳定返回冲突
- 远端不新时是否不报冲突
- 下载远端备份路径是否始终返回正确内容

### 3. 现有测试分层基本正确，但还缺“仓库层”这一层

目前已有：

- 纯加解密测试
- WebDAV XML 解析测试
- DTO 映射测试
- 最小同步页 UI 测试

缺的是更靠近业务核心的仓库层测试，也就是最能证明备份/恢复/冲突逻辑是否真的可靠的那部分。

## 设计原则

### 原则一：测试优先，生产代码最小修改

实现顺序必须是：

1. 先写失败测试
2. 运行确认红灯
3. 只改最小实现让测试转绿
4. 回归验证完整测试命令

任何“顺手优化”“顺手重构”都不属于本轮目标。

### 原则二：优先主机单元测试

本轮应尽量把新增验证放在 `app/src/test/`：

- 运行更快
- 更稳定
- 更适合仓库层逻辑

只有必须依赖 Android 组件或 Compose 交互时，才继续使用 `androidTest`。本轮预期不新增新的 `androidTest` 重点场景。

唯一例外是：如果 `BackupRepositoryImpl` 的恢复路径因为 `ChatDatabase.withTransaction` 无法在主机测试中稳定验证，则允许把“恢复写回行为”下沉为一个最小 instrumented test，或者通过极小的 seam 抽取把事务执行与数据映射分开后继续做主机测试。目标仍然是最小可测改动，而不是重写结构。

### 原则三：通过假对象隔离仓库依赖

`BackupRepositoryImpl` 与 `SyncRepositoryImpl` 的测试应尽量避免依赖真实 Room 数据库、真实 DataStore 或真实 WebDAV 服务。

建议在测试内使用轻量假对象或 stub：

- DAO fake
- `SettingRepository` fake
- `BackupRepository` fake
- `WebDavRepository` fake

如果 `BackupRepositoryImpl` 的恢复路径无法避免 `ChatDatabase.withTransaction`，允许额外引入最小 `FakeChatDatabase` 或极小事务 seam；但这只能服务于测试落地，不能扩展为大重构。

只保留与行为相关的最小协作面，避免测试被外部细节拖慢或变脆。

## 方案选择

### 方案 A：只补纯工具测试

优点：最简单，速度快。  
缺点：对当前最关键的备份/恢复/冲突主流程帮助有限。

### 方案 B：仓库层测试优先，只修测试暴露的问题

优点：

- 直接覆盖最关键的业务逻辑
- 改动面可控
- 最符合当前“功能已通、先补底盘”的阶段需求

缺点：

- 需要为仓库层构造少量 fake/stub

### 方案 C：顺手重构同步模块再补测试

优点：代码可能更整洁。  
缺点：改动面扩大，容易把本轮工作从“补强”变成“重写”。

本轮采用 **方案 B**。

## 详细设计

### 一、`BackupRepositoryImpl` 测试设计

新增测试文件建议：

- `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplTest.kt`

如果恢复路径因为 `ChatDatabase.withTransaction` 无法在主机测试里稳定运行，则拆成：

- `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplTest.kt`：覆盖导出、解析、错误密码、坏 JSON、schema 错误
- `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplInstrumentedTest.kt`：只覆盖恢复写回与“数据库/设置调用顺序”的最小行为

测试应覆盖以下行为：

1. **导出时生成准确摘要**
   - 聊天数、消息数、角色数与 fake DAO 返回值一致
   - `containsSecrets` 在任一平台 token 非空时为 `true`

2. **导出结果包含加密元信息**
   - `schemaVersion` 正确
   - `encryption.enabled` 为 `true`
   - `algorithm == "AES/GCM/NoPadding"`
   - `kdf == "PBKDF2WithHmacSHA256"`
   - `salt` / `iv` 非空
   - `iterations > 0`

3. **恢复时错误密码失败**
   - 使用正确密码导出
   - 使用错误密码恢复
   - 断言抛出 `IllegalArgumentException`
   - 断言消息为 `Invalid backup password`

4. **损坏 JSON 失败**
   - 非法 JSON
   - 缺少关键字段的 JSON

5. **不支持 schema 失败**
   - `schemaVersion != 1` 时抛出 `IllegalArgumentException`
   - 异常消息包含 `Unsupported backup schema version`

6. **恢复成功时按预期写回**
   - fake DAO 收到清空和重新写入调用
   - `SettingRepository` 收到更新的平台、主题、流式样式

7. **恢复过程中的部分失败行为被锁定**
   - 如果 `SettingRepository` 更新阶段抛错，测试必须明确当前实现会留下什么状态
   - 本轮允许先把它写成“当前合同测试”，再由测试结果决定是否做最小修复

### 二、`SyncRepositoryImpl` 测试设计

新增测试文件建议：

- `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/SyncRepositoryImplTest.kt`

测试应覆盖以下行为：

1. **配置归一化与保存委托**
   - `testWebDavConnection` 会把 `baseUrl` 去尾 `/`
   - `remotePath` 会去首尾 `/`
   - `saveWebDavConfig` 保存时会写入归一化后的值

2. **配置读取与清理委托**
   - `getWebDavConfig` 直接返回存储值
   - `clearWebDavConfig` 会写入 `null`

3. **没有 WebDAV 配置时报错**
   - `listRemoteBackups`
   - `uploadBackup`
   - `downloadRemoteBackup`

4. **没有远端备份时不报冲突**

5. **远端较新时返回冲突对象**
   - 校验 `remoteFileName`
   - 校验本地/远端摘要与时间戳

6. **远端不较新时不报冲突**

7. **远端备份损坏或 schema 不支持时失败**
   - `detectUploadConflict()` 下载并解析远端备份时，如果远端内容损坏，必须明确失败，不允许误判为“无冲突”

8. **`uploadBackup(overwrite = false)` 遇冲突时失败**
   - 保持当前行为，确保不会静默覆盖

9. **`uploadBackup(overwrite = true)` 会上传**

10. **`downloadRemoteBackup` 直接转发远端内容**

关于 `getWebDavPassword`：本轮不强制覆盖 Android Keystore 解密路径本身；如果测试阶段证明这条路径阻塞可靠性补强，再单独抽 seam，不在本轮默认扩张。

### 三、错误语义补强策略

如果测试暴露以下问题，允许做最小修复：

- 异常消息不稳定，导致 UI 无法统一提示
- 失败后发生部分状态写入
- 冲突检测在边界条件下误判
- 备份摘要与真实数据不一致

修复策略应优先：

1. 局部收敛错误语义
2. 保持现有接口不变
3. 不为测试而重写架构

为避免实现阶段反复争论断言合同，本轮先以当前代码已经形成的稳定语义为准：

- 备份密码错误：断言 `IllegalArgumentException`，消息为 `Invalid backup password`
- schema 不支持：断言 `IllegalArgumentException`，消息包含 `Unsupported backup schema version`
- 损坏 JSON：至少断言“失败而不是成功恢复”，异常类型可以跟随当前序列化实现，除非测试证明 UI 依赖统一语义

### 四、测试辅助结构

为了让仓库层测试保持简单，允许在测试文件内部声明最小 fake：

- `FakeChatRoomDao`
- `FakeMessageDao`
- `FakeAiMaskDao`
- `FakeSettingRepository`
- `FakeBackupRepository`
- `FakeWebDavRepository`
- `FakeChatDatabase` 或最小事务 seam（仅在恢复测试确实需要时引入）

要求：

- 只实现测试实际需要的行为
- 不额外抽成生产代码
- 命名直接表达用途，不做过度抽象

## 验证策略

本轮完成后至少要运行：

```bash
./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImplTest"
./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.SyncRepositoryImplTest"
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

如果 `BackupRepositoryImpl` 的恢复路径被下沉到最小 instrumented test，再补跑：

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.data.sync.BackupRepositoryImplInstrumentedTest
```

如果为了修复问题而触碰到同步页文案或状态流，再补跑：

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.setting.SyncScreenTest
```

## 验收标准

满足以下条件即可认为本轮完成：

1. 新增 `BackupRepositoryImpl` 测试
2. 新增 `SyncRepositoryImpl` 测试
3. 所有新增测试都先经历红灯再转绿
4. 只修复测试明确暴露的问题
5. `:app:testDebugUnitTest` 通过
6. `:app:assembleDebug` 通过

## 后续延伸

本轮结束后，再考虑下一层迭代：

- 备份 schema migration 策略
- 更细粒度的恢复前摘要页
- WebDAV 远端备份删除与保留策略
- 更详细的云端错误分类与用户提示
