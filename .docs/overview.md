# 项目概览

## 项目定位
- 项目名：GPT Mobile
- 类型：Android 聊天应用
- 技术栈：Kotlin、Jetpack Compose、Hilt、Room、Ktor、DataStore

## 目录重点
- 应用代码：`app/src/main/kotlin/dev/chungjungsoo/gptmobile`
- UI 层：`app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui`
- 数据层：`app/src/main/kotlin/dev/chungjungsoo/gptmobile/data`
- 工具函数：`app/src/main/kotlin/dev/chungjungsoo/gptmobile/util`
- 工作流：`.github/workflows`

## 当前状态
- 聊天输入框已支持更稳定的多行输入
- 支持输入框全屏展开编辑
- 已接入新的 release 签名链路

## 保留原则
- 根目录 `README.md`：对外项目说明，保留
- 根目录 `AGENTS.md`：项目执行规范，保留
- `metadata/`：商店/分发元数据，保留
- `.github/workflows/`：CI/CD 工作流，保留
- `.idea/`、`.vscode/`：当前仓库已跟踪，除非明确废弃，否则不清理
