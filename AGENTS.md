# AGENTS.md - GPT Mobile Android 项目

## 项目概述
GPT Mobile 是一个 Android 聊天应用，支持同时与多个 AI 模型聊天。使用现代 Android 开发技术栈。

## 构建与运行

### 环境要求
- JDK 17
- Android SDK 35
- Gradle 8.7.3

### 构建命令
```bash
# 构建调试 APK
./gradlew assembleDebug

# 构建发布 APK
./gradlew assembleRelease

# 构建 App Bundle
./gradlew bundleRelease

# 清理构建
./gradlew clean
```

### 测试命令
```bash
# 运行所有单元测试
./gradlew test

# 运行特定模块的单元测试
./gradlew :app:test

# 运行单个单元测试类
./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.ExampleUnitTest"

# 运行单个单元测试方法
./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.ExampleUnitTest.addition_isCorrect"

# 运行 Android 仪器测试（需要连接设备或模拟器）
./gradlew connectedAndroidTest

# 运行单个仪器测试类
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.ExampleInstrumentedTest

# 运行所有检查（包括 lint 和测试）
./gradlew check
```

### 代码质量检查
```bash
# 项目使用 ktlint 1.3.1 进行 Kotlin 代码格式检查
# 注意：项目未配置本地 ktlint Gradle 插件，代码格式检查通过 GitHub Actions 或 IDE 插件进行
# 在 Android Studio 中，使用 "Reformat Code" (Ctrl+Alt+L / Cmd+Option+L) 可根据 .editorconfig 规则格式化

# 运行 lint 检查
./gradlew lint

# 运行 CodeQL 分析（通过 GitHub Actions）
```

## 项目结构

```
app/
├── src/
│   ├── main/
│   │   ├── kotlin/dev/chungjungsoo/gptmobile/
│   │   │   ├── data/          # 数据层（数据库、网络、仓库）
│   │   │   ├── presentation/  # 表示层（UI、ViewModel）
│   │   │   └── util/          # 工具类
│   │   └── res/               # 资源文件
│   ├── test/                  # 单元测试
│   └── androidTest/           # Android 仪器测试
└── build.gradle.kts
```

## 代码风格指南

### Kotlin 代码风格
- 使用 Android Studio 官方代码风格
- 缩进：4 个空格
- 最大行长度：无限制（`max_line_length = off`）
- 使用尾随逗号：禁用（`ij_kotlin_allow_trailing_comma = false`）
- 导入布局：`*`（通配符导入禁用）
- 函数命名：允许 `@Composable` 函数使用非驼峰命名

### Compose 特定规则
- 状态持有者命名：匹配 `.*ViewModel`、`.*Presenter`、`.*Component` 模式
- `@Composable` 函数可以忽略 ktlint 函数命名规则

### 命名约定
- 包名：`dev.chungjungsoo.gptmobile`
- 类名：PascalCase
- 函数名：camelCase（`@Composable` 函数除外）
- 变量名：camelCase
- 常量名：UPPER_SNAKE_CASE

### 导入规范
- 禁止使用通配符导入（`java.util.*`、`kotlinx.android.synthetic.**` 除外）
- 导入顺序：按字母顺序排列
- 使用显式导入

## 测试指南

### 单元测试
- 使用 JUnit 4
- 测试类命名：`*Test`
- 测试方法命名：`@Test` 注解
- 示例：`ExampleUnitTest.kt`

### Android 仪器测试
- 使用 Espresso 和 Compose UI 测试
- 测试类命名：`*InstrumentedTest`
- 示例：`ExampleInstrumentedTest.kt`

### 测试配置
- 测试运行器：`androidx.test.runner.AndroidJUnitRunner`
- 最低 SDK：31（Android 12）

## 依赖管理

### 版本目录
使用 Gradle 版本目录管理依赖（`gradle/libs.versions.toml`）。

主要依赖：
- Kotlin：2.0.20
- Compose BOM：2024.11.00
- Hilt：2.52
- Room：2.6.1
- Ktor：2.3.12
- Navigation：2.8.4

### 添加新依赖
1. 在 `gradle/libs.versions.toml` 中添加版本和库定义
2. 在 `app/build.gradle.kts` 中添加依赖引用

## CI/CD 工作流程

### GitHub Actions
1. **ktlint.yml**：Kotlin 代码格式检查（PR 到 main 分支）
2. **debug-build.yml**：构建调试 APK（PR 到 main 分支）
3. **release-build.yml**：构建发布版本（手动触发）
4. **codeql.yml**：代码安全分析（推送到 main、PR、每周一）

### 检查项
- Kotlin 代码格式（ktlint 1.3.1）
- 调试构建成功
- 发布构建成功（需要签名）
- CodeQL 安全分析

## 架构模式

### 现代 Android 架构
- 单 Activity 架构
- Jetpack Compose UI
- MVVM 模式
- 依赖注入（Hilt）
- 响应式编程（Kotlin Flow）

### 数据层
- Room 数据库
- DataStore 偏好设置
- 网络层（Ktor）
- 仓库模式

## 注意事项

### 代码质量
- 遵循 `.editorconfig` 中的 ktlint 配置
- 禁止使用 `@Suppress` 注释忽略 lint 错误（除非有充分理由）
- 保持代码简洁，避免过度注释

### 性能
- 使用 R8 完整模式进行代码压缩
- 启用非传递性 R 类
- 避免内存泄漏

### 兼容性
- 最低 SDK：31（Android 12）
- 目标 SDK：35（Android 15）
- 支持动态主题（Material You）
- 支持深色模式

## 快速开始

1. 克隆仓库
2. 在 Android Studio 中打开项目
3. 等待 Gradle 同步完成
4. 连接设备或启动模拟器
5. 运行 `./gradlew assembleDebug` 构建应用
6. 安装调试 APK 到设备

## 故障排除

### 构建失败
- 检查 JDK 版本是否为 17
- 确保 Android SDK 35 已安装
- 运行 `./gradlew clean` 清理构建缓存

### 测试失败
- 确保设备/模拟器已连接
- 检查测试设备 SDK 版本是否 >= 31
- 运行 `./gradlew test --info` 获取详细错误信息

### 代码格式错误
- 运行 ktlint 格式化：项目使用 Android Studio 格式化工具
- 检查 `.editorconfig` 配置
- 遵循 Kotlin 官方代码风格