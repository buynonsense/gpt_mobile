# 发布与签名说明

## 版本位置
- Android 版本号定义在：`app/build.gradle.kts`
- 当前版本：
  - `versionCode = 18`
  - `versionName = "0.9.3"`

## Release 工作流
- 文件：`.github/workflows/release-build.yml`
- 触发方式：`workflow_dispatch`
- 说明：不会因推送 tag 自动触发，需要手动运行 workflow

## Release 签名 secrets
工作流依赖以下 GitHub secrets：
- `APP_KEYSTORE`
- `KEY_ALIAS`
- `KEYSTORE_PASSWORD`
- `KEY_PASSWORD`

## 当前签名策略
- keystore 类型：`JKS`
- APK 签名：`apksigner`
- AAB 签名：`jarsigner`
- workflow 已显式传入 alias、keystore password、key password

## 本地签名文件备份
- 路径：`/Users/buynonsense/.android/gpt_mobile/release-keystore.jks`

## 注意事项
- 这个 keystore 是后续继续发布同一应用版本链路的关键文件，不要删除
- 如果重新生成 keystore，历史已安装版本可能无法直接覆盖更新

## 当前 release 工作流兼容性处理
- `release-build.yml` 中：
  - `actions/checkout` 已升级到 `v5`
  - `actions/setup-java` 已升级到 `v5`
  - `actions/upload-artifact` 已升级到 `v6`
  - `android-actions/setup-android` 当前保持 `v3`
- 其他 workflow 仍可能保留旧版本 action，后续如需统一，再逐个工作流审查
