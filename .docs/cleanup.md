# 仓库清理规则

## 已清理内容
- 删除根目录无用文件：`.DS_Store`
- 删除过时文档：旧 `.docs/agents.md`、`.docs/todo.md`

## 无用文件判定原则
- 明确的系统垃圾文件：例如 `.DS_Store`
- 明显过期且与当前项目状态冲突的内部文档
- 未被代码、构建、工作流、发布链引用的临时产物

## 不应随意删除的内容
- `README.md`、`AGENTS.md`
- `.github/workflows/**`
- `metadata/**`
- `images/**`
- `gradle/**`、`gradlew`、`gradlew.bat`
- `.idea/**`、`.vscode/**`（当前已被仓库跟踪）

## 后续清理建议
- 若要继续精简仓库，优先检查：
  - 是否仍需要提交 `.idea/` 中全部文件
  - 是否需要保留 `gradlew.bat`
  - README 中指向旧上游仓库的链接是否需要整体替换

## 操作原则
- 先查引用，再删除
- 不删除仍被 CI、Gradle、Android Studio、发布流程使用的文件
- 文档统一放在 `.docs/` 目录维护，避免零散重复
