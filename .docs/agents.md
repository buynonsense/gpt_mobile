# 项目说明

GPTMobile：一个 Android（Jetpack Compose）聊天应用。

## 关键模块
- UI：`app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui`
- 工具：`app/src/main/kotlin/dev/chungjungsoo/gptmobile/util`

## 数据/契约（简要）
- 聊天消息内容可能包含 Markdown。
- “复制纯文本”会把 Markdown 转成更接近纯文本的形式。

## 当前里程碑
- 修复“复制纯文本”按钮点击崩溃（Regex 解析失败）。
