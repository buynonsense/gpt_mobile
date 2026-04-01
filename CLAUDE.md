# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project basics

- Android app written in Kotlin.
- Single Gradle module: `:app`.
- Toolchain in code: Java 17, compile/target SDK 35, min SDK 31.
- Main app package: `dev.chungjungsoo.gptmobile`.
- Dependency versions are managed in `gradle/libs.versions.toml`.

## Common commands

Run all commands from the repository root.

```bash
# Build debug APK
./gradlew assembleDebug

# Install debug build to a connected device/emulator
./gradlew :app:installDebug

# Build release artifacts
./gradlew assembleRelease
./gradlew bundleRelease

# Run unit tests
./gradlew test
./gradlew :app:testDebugUnitTest

# Run a single unit test class
./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.util.MarkdownUtilsTest"

# Run a single unit test method
./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.util.MarkdownUtilsTest.someMethodName"

# Run instrumented tests on a connected device/emulator
./gradlew connectedDebugAndroidTest

# Run a single instrumented test class
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.ExampleInstrumentedTest

# Android lint
./gradlew lint

# Full verification
./gradlew check
```

## Architecture overview

### App structure

The app is a single-activity Compose application:

- `presentation/ui/main/MainActivity.kt` owns app startup, splash handling, and root navigation setup.
- `presentation/common/NavigationGraph.kt` defines the full navigation graph.
- `presentation/GPTMobileApp.kt` is the Hilt application entry point.

Navigation is organized into a few main flows:

- **Home / role entry**: role-first landing page instead of a chat-history-first landing page.
- **Setup flow**: initial provider selection, token entry, model selection, Ollama URL setup.
- **Chat flow**: open an existing chat or create one from a role.
- **Settings flow**: global app settings and per-provider settings.
- **Role/archive/search flows**: manage roles, archived roles/chats, and full-message search.

### State and UI pattern

The codebase mostly follows a simple MVVM shape:

- Compose screens live under `presentation/ui/...`
- Screen state is held in `*ViewModel` classes using `MutableStateFlow` / `StateFlow`
- Dependency injection is done with Hilt modules under `di/`

A notable pattern in navigation is that nested setup/settings destinations share a parent-scoped ViewModel via `hiltViewModel(parentEntry)` in `NavigationGraph.kt`.

### Persistence model

There are two local persistence mechanisms:

1. **Room** for chat/role data
   - `data/database/ChatDatabase.kt`
   - DAOs in `data/database/dao/`
   - Entities include chat rooms, messages, and roles (`AiMask`)
   - Migrations are centralized in `data/database/Migrations.kt`

2. **DataStore Preferences** for app/provider settings
    - `data/datastore/SettingDataSourceImpl.kt`
    - Stores enabled providers, API URLs, tokens, model names, temperature/top-p, prompts, theme settings, streaming style, and WebDAV sync config

3. **Backup / sync layer** for full backup and restore
   - `data/sync/BackupRepositoryImpl.kt`
   - `data/sync/SyncRepositoryImpl.kt`
   - `data/sync/WebDavRepositoryImpl.kt`
   - `data/sync/PasswordCryptoHelper.kt`
   - Full backups include Room data + DataStore settings + API keys
   - Backup payload is exported as plain JSON without a backup password
   - WebDAV password is stored locally using Android Keystore-backed encryption

`DatabaseModule.kt` wires Room and DAOs. `DataStoreModule.kt` wires the preferences store.

### Domain model: role-first chat UX

Despite older `AiMask` naming in the data layer, the app’s user-facing model is now role-centric:

- The home screen shows grouped roles.
- Opening a role resolves to its latest active chat or creates a new one.
- There is always a default role (`AI助手`).
- Archiving a role also archives its chats; restoring a role restores its chats.

The key logic is split across:

- `data/repository/AiMaskRepositoryImpl.kt`
- `data/repository/ChatRepositoryImpl.kt`
- `presentation/ui/home/HomeViewModel.kt`

When changing role behavior, check all three so home-screen grouping, role lifecycle, and chat creation stay consistent.

### Chat pipeline

The most important non-obvious subsystem is the multi-provider chat pipeline.

`ChatViewModel.kt` orchestrates one user prompt fan-out to multiple providers at once:

- It parses enabled providers from navigation args.
- It keeps per-provider loading state and per-provider in-progress assistant messages.
- It starts one request per enabled provider.
- It observes each provider stream independently.
- Once all enabled providers are idle, it syncs user + assistant messages into Room in one save pass.

`ChatRepositoryImpl.kt` is the provider adapter layer:

- **OpenAI / Groq / Ollama** use the OpenAI-compatible client.
- **Anthropic** uses a custom Ktor-based API wrapper in `data/network/AnthropicAPI*.kt`.
- **Google Gemini** uses the Google Generative AI SDK.

The repository also handles:

- converting stored message history into provider-specific request formats
- fetching model lists per provider, with fallback model lists on failure
- chat-room creation/reuse for a role
- title generation and persistence
- full-message search via Room

If you change chat behavior, read both `ChatViewModel.kt` and `ChatRepositoryImpl.kt` first; most bugs come from their interaction rather than a single screen.

### Streaming rendering

Streaming responses are not rendered as plain append-only text only.

`ChatViewModel.kt` uses `IncrementalMarkdownParser` to accumulate streamed chunks into markdown blocks per provider. This is tied to the app’s selectable streaming style setting from `SettingRepository`.

If you touch streaming UX, inspect both:

- `presentation/ui/chat/ChatViewModel.kt`
- `util/IncrementalMarkdownParser.kt`

### Settings model

Provider settings are normalized through `SettingRepository`:

- API enablement
- API URL
- token
- model
- temperature
- top-p
- system prompt

Defaults are applied in the repository layer, not only in UI.

This matters because setup screens and settings screens both read/write the same underlying provider configuration, but they do so through different ViewModels (`SetupViewModel`, `SettingViewModel`).

### Backup and sync flow

The app now has a dedicated sync screen under settings:

- `presentation/ui/setting/SyncScreen.kt`
- `presentation/ui/setting/SyncViewModel.kt`

The sync flow currently supports:

- generating a full backup JSON from local data without a backup password
- saving that backup to a user-selected local file through Android SAF
- importing a backup JSON from a user-selected local file through Android SAF
- restoring imported backup content into local storage without password confirmation
- switching between local backup and WebDAV sync sections through top-level cards in `SyncScreen.kt`
- editing WebDAV config through a dialog instead of inline fields in the main screen
- listing WebDAV backups
- uploading local backup files to WebDAV
- downloading a remote WebDAV backup into the restore area for manual confirmation

Conflict handling is intentionally manual:

- uploads detect when the latest remote backup is newer than the current local export
- users can either overwrite the remote backup or load the remote backup into the restore area and then explicitly confirm local restore
- there is no automatic merge

## Current test surface

Test coverage is currently light. The repository includes:

- unit tests under `app/src/test/`
- instrumented tests under `app/src/androidTest/`

Notable current tests now include:

- `app/src/test/kotlin/dev/chungjungsoo/gptmobile/util/MarkdownUtilsTest.kt`
- `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/PasswordCryptoHelperTest.kt`
- `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/WebDavXmlParserTest.kt`
- `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/model/BackupModelsTest.kt`
- `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/BackupRepositoryImplTest.kt`
- `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/SyncRepositoryImplTest.kt`
- `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/SyncErrorClassifierTest.kt`
- `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/sync/WebDavRepositoryImplErrorTest.kt`
- `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/datastore/SettingDataSourceImplSyncStatusTest.kt`
- `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncScreenTest.kt`
- `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SyncViewModelStatusTest.kt`

For sync-related changes, prefer running:

```bash
./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.PasswordCryptoHelperTest"
./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.WebDavXmlParserTest"
./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.sync.model.BackupModelsTest"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.setting.SyncScreenTest
./gradlew :app:assembleDebug
```

## Notes from existing repo guidance

The repository already contains `AGENTS.md`; the useful repo-specific parts are:

- use Java 17 / Android SDK 35
- use Gradle version catalog entries in `gradle/libs.versions.toml` when adding dependencies
- prefer standard Gradle commands above for build/test/lint work
