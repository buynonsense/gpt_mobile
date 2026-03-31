# AGENTS.md - GPT Mobile

## 目的
- 本文件提供给仓库内执行任务的 agentic coding agents。
- 先以真实配置文件为准，再参考本文件总结。
- 规则优先级：`app/build.gradle.kts`、`gradle/libs.versions.toml`、`.editorconfig`、`.github/workflows/*.yml`、`README.md`、本文件。

## 仓库概览
- 项目是单模块 Android 应用，模块只有 `:app`。
- 应用包名与 namespace：`dev.chungjungsoo.gptmobile`。
- 技术栈：Kotlin、Jetpack Compose、Material 3、Hilt、Room、DataStore、Ktor、Navigation Compose。
- 架构方向：Single Activity + MVVM + Repository。
- 最低 SDK：31。
- 目标 SDK：35。
- 编译 SDK：35。
- Java / JVM 目标版本：17。

## 关键文件
- 根设置：`settings.gradle.kts`
- 根构建脚本：`build.gradle.kts`
- App 构建脚本：`app/build.gradle.kts`
- 版本目录：`gradle/libs.versions.toml`
- 格式规则：`.editorconfig`
- 说明文档：`README.md`
- 主代码目录：`app/src/main/kotlin/dev/chungjungsoo/gptmobile/`
- 单元测试目录：`app/src/test/kotlin/`
- 仪器测试目录：`app/src/androidTest/kotlin/`
- CI 工作流：`.github/workflows/`

## 外部规则文件检查结果
- 仓库内未发现 `.cursor/rules/`。
- 仓库内未发现 `.cursorrules`。
- 仓库内未发现 `.github/copilot-instructions.md`。
- 因此无需合并额外的 Cursor 或 Copilot 仓库规则。

## 环境要求
- JDK 17。
- Android SDK 35。
- Gradle 8.7.3。
- 本地执行 Android 仪器测试时需要已连接设备或模拟器，且设备 SDK >= 31。

## 常用构建命令
```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
./gradlew clean
```

## 常用检查命令
```bash
./gradlew lint
./gradlew check
./gradlew test
./gradlew :app:test
./gradlew connectedAndroidTest
```

## 运行单个测试
- 单个单元测试类：
```bash
./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.ExampleUnitTest"
```
- 单个单元测试方法：
```bash
./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.ExampleUnitTest.addition_isCorrect"
```
- 单个仪器测试类：
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.ExampleInstrumentedTest
```
- 仓库文档里没有已验证的“单个仪器测试方法”命令；除非先在本地确认，不要把未验证命令写死进提交。

## 测试定位示例
- 单元测试示例类：`dev.chungjungsoo.gptmobile.ExampleUnitTest`
- 单元测试示例类：`dev.chungjungsoo.gptmobile.util.MarkdownUtilsTest`
- 单元测试示例类：`dev.chungjungsoo.gptmobile.data.sync.PasswordCryptoHelperTest`
- 单元测试示例类：`dev.chungjungsoo.gptmobile.data.sync.WebDavXmlParserTest`
- 单元测试示例类：`dev.chungjungsoo.gptmobile.data.sync.model.BackupModelsTest`
- 仪器测试示例类：`dev.chungjungsoo.gptmobile.ExampleInstrumentedTest`
- 仪器测试示例类：`dev.chungjungsoo.gptmobile.presentation.ui.setting.SyncScreenTest`
- 改动工具类、解析逻辑、纯 Kotlin 逻辑时，优先补或跑 `testDebugUnitTest`。
- 改动 Compose UI、导航、Activity 集成、资源交互时，再考虑 `connectedDebugAndroidTest`。

## 数据同步模块提示
- 数据同步入口在设置页，核心 UI 位于 `presentation/ui/setting/SyncScreen.kt` 与 `SyncViewModel.kt`。
- 同步数据层位于 `data/sync/`，核心实现包括：`BackupRepositoryImpl.kt`、`SyncRepositoryImpl.kt`、`WebDavRepositoryImpl.kt`、`PasswordCryptoHelper.kt`。
- 全量备份范围包括 Room 中的聊天 / 消息 / 角色，以及 DataStore 中的设置与 API key。
- 备份文件使用“用户输入的备份密码”加密；WebDAV 密码本地保存时使用 Android Keystore 加密。
- 本地导入导出通过 Android SAF 实现；不要把文件读写逻辑塞进 ViewModel。
- 云同步首版只做全量备份，不做自动合并。
- 检测到冲突时，必须保持手动决策：允许用户覆盖云端，或先下载云端备份到恢复区，再由用户确认是否恢复到本地。

## CI 与质量门禁
- `ktlint.yml`：PR 到 `main` 时运行 Kotlin 格式检查。
- `debug-build.yml`：PR 到 `main` 时构建 `assembleDebug`。
- `codeql.yml`：`push main`、PR 到 `main`、每周定时运行 CodeQL。
- `release-build.yml`：手动触发，构建并签名 release APK / AAB。
- CI 中的 ktlint 使用 GitHub Action `ScaCap/action-ktlint@master`，版本为 `1.3.1`。
- 仓库未见本地 Gradle ktlint task 证据，不要默认建议 `./gradlew ktlintCheck` 或 `./gradlew ktlintFormat`。

## 依赖与版本策略
- 依赖版本统一维护在 `gradle/libs.versions.toml`。
- 新增依赖时先改 `gradle/libs.versions.toml`，再在 `app/build.gradle.kts` 引用。
- 当前关键版本：Kotlin 2.0.20、AGP 8.7.3、Compose BOM 2024.11.00、Hilt 2.52、Room 2.6.1、Ktor 2.3.12、Navigation 2.8.4。
- 项目使用 KSP、Parcelize、Kotlin Serialization、Compose Compiler 插件。

## 代码组织
- `data/`：数据库、DataStore、网络、DTO、Repository 实现。
- `presentation/`：Compose UI、ViewModel、导航、主题。
- `di/`：Hilt 模块。
- `util/`：工具类与扩展。
- 修改前先在相邻目录搜索是否已有类似实现，优先复用现有能力，不要平行造轮子。

## Kotlin 与格式规范
- 遵循 `.editorconfig` 中的 Kotlin / ktlint 配置。
- 使用 Android Studio code style。
- 缩进 4 个空格。
- 换行符使用 LF。
- 文件结尾保留换行。
- `max_line_length = off`，但仍应优先写可读代码，不要无意义拉长单行。
- 禁止尾随逗号：声明处和调用处都关闭。
- 除了 IDE/格式化工具必要变更，不做无关 whitespace 清理。

## 导入规范
- 默认使用显式导入。
- 仓库源码中未发现 Kotlin 星号导入的实际使用；新增代码不要引入 `*` 导入。
- `.editorconfig` 中仅为 `java.util.*`、`kotlinx.android.synthetic.**` 配置了 import-on-demand 白名单，但不代表应主动使用。
- 导入布局跟随 IDE 与 `.editorconfig`，不要手工制造与现有格式冲突的导入顺序。

## 命名规范
- 包名保持 `dev.chungjungsoo.gptmobile` 体系。
- 类、对象、接口、Composable 页面名使用 PascalCase。
- 普通函数、属性、局部变量使用 camelCase。
- 常量使用 UPPER_SNAKE_CASE。
- 状态持有者命名应匹配 `.editorconfig` 里的模式：`.*ViewModel`、`.*Presenter`、`.*Component`。
- `@Composable` 函数允许使用不满足普通 ktlint 函数命名规则的命名方式，但应与现有页面/API 命名保持一致。
- 测试类命名保持 `*Test` 或 `*InstrumentedTest` 风格。

## 类型与 API 设计
- 保持 Kotlin 强类型风格，优先明确参数与返回值语义。
- 新增公开函数时，优先让返回类型、空值语义、错误分支清晰可读。
- 不要为了省事把多种语义塞进字符串、Map 或裸 `Any` 风格容器。
- 优先复用现有 DTO、entity、repository 接口与 Flow 状态模型。
- 新增数据结构时，先检查 `data/dto/`、`data/model/`、`data/database/entity/` 是否已有近似类型。

## Compose 与 Android 约定
- 保持 Single Activity + Navigation Compose 的现有结构。
- UI 状态优先放在 ViewModel 或现有 state holder 中，不要把复杂业务状态散落在 Composable 内。
- 改 UI 前先看同目录现有 screen / dialogs / common 组件，尽量复用。
- 保持 Material 3 与现有主题体系一致，不要引入突兀的新设计语言。
- 涉及字符串、资源、权限、导出文件等 Android 行为时，考虑生命周期、上下文和异常分支。

## 错误处理与日志
- 不要静默吞异常。
- 捕获异常后，优先记录带上下文的日志，至少包含模块名、关键参数或操作目的。
- 能回退时提供明确回退值；不能回退时返回可诊断的错误状态或用户可感知反馈。
- 仓库中较好的参考实现：`app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/repository/ChatRepositoryImpl.kt`。
- UI 侧带用户提示的参考实现：`app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatScreen.kt`。
- 仓库中存在少量直接返回空列表或简单错误块的旧实现；新增代码不要沿用这种弱错误处理模式。

## 修改策略
- 每次只做最小必要修改。
- 先阅读相关文件，再动手改。
- 优先局部修复，不做顺手的大规模重构。
- 不要删除或覆盖用户未要求处理的现有改动。
- 不要新增无意义注释；仅在复杂、非显然逻辑处补最少说明。
- 非必要不要新增 `@Suppress`；仓库已有构建脚本级 `@file:Suppress("UnstableApiUsage")`，修改前先确认是否是工具链所需。

## 提交前验证建议
- 改动 Kotlin 业务逻辑后，至少运行相关单元测试；拿不准时运行 `./gradlew :app:testDebugUnitTest`。
- 改动 Compose / Android 集成后，至少运行 `./gradlew assembleDebug`。
- 改动资源、清单、依赖、数据库或导航时，优先运行 `./gradlew check` 或最接近的完整验证命令。
- 只有在本地具备设备/模拟器条件时再跑 `connectedAndroidTest`。

## 给代理的执行提醒
- 先看配置文件和现有实现，再下结论。
- 先复用，再新增。
- 先验证，再声称命令可用。
- 回答仓库问题时，把“仓库事实”和“你的建议”区分清楚。
