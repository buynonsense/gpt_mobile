package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.StreamingStyle
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.data.sync.model.SyncStatusSnapshot
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig
import javax.inject.Inject

class SettingRepositoryImpl @Inject constructor(
    private val settingDataSource: SettingDataSource
) : SettingRepository {

    override suspend fun fetchPlatforms(): List<Platform> = ApiType.entries.map { apiType ->
        val status = settingDataSource.getStatus(apiType)
        val apiUrl = when (apiType) {
            ApiType.OPENAI -> settingDataSource.getAPIUrl(apiType) ?: ModelConstants.OPENAI_API_URL
            ApiType.ANTHROPIC -> settingDataSource.getAPIUrl(apiType) ?: ModelConstants.ANTHROPIC_API_URL
            ApiType.GOOGLE -> settingDataSource.getAPIUrl(apiType) ?: ModelConstants.GOOGLE_API_URL
            ApiType.GROQ -> settingDataSource.getAPIUrl(apiType) ?: ModelConstants.GROQ_API_URL
            ApiType.OLLAMA -> settingDataSource.getAPIUrl(apiType) ?: ""
        }
        val token = settingDataSource.getToken(apiType)
        val model = settingDataSource.getModel(apiType)
        val temperature = settingDataSource.getTemperature(apiType)
        val topP = settingDataSource.getTopP(apiType)
        val systemPrompt = when (apiType) {
            ApiType.OPENAI -> settingDataSource.getSystemPrompt(ApiType.OPENAI) ?: ModelConstants.OPENAI_PROMPT
            ApiType.ANTHROPIC -> settingDataSource.getSystemPrompt(ApiType.ANTHROPIC) ?: ModelConstants.DEFAULT_PROMPT
            ApiType.GOOGLE -> settingDataSource.getSystemPrompt(ApiType.GOOGLE) ?: ModelConstants.DEFAULT_PROMPT
            ApiType.GROQ -> settingDataSource.getSystemPrompt(ApiType.GROQ) ?: ModelConstants.DEFAULT_PROMPT
            ApiType.OLLAMA -> settingDataSource.getSystemPrompt(ApiType.OLLAMA) ?: ModelConstants.DEFAULT_PROMPT
        }

        Platform(
            name = apiType,
            enabled = status == true,
            apiUrl = apiUrl,
            token = token,
            model = model,
            temperature = temperature,
            topP = topP,
            systemPrompt = systemPrompt
        )
    }

    override suspend fun fetchThemes(): ThemeSetting = ThemeSetting(
        dynamicTheme = settingDataSource.getDynamicTheme() ?: DynamicTheme.OFF,
        themeMode = settingDataSource.getThemeMode() ?: ThemeMode.SYSTEM
    )

    override suspend fun fetchStreamingStyle(): StreamingStyle =
        settingDataSource.getStreamingStyle() ?: StreamingStyle.TYPEWRITER

    override suspend fun fetchWebDavConfig(): WebDavConfig? = settingDataSource.getWebDavConfig()

    override suspend fun fetchSyncStatusSnapshot(): SyncStatusSnapshot? = settingDataSource.getSyncStatusSnapshot()

    override suspend fun updatePlatforms(platforms: List<Platform>) {
        platforms.forEach { platform ->
            settingDataSource.updateStatus(platform.name, platform.enabled)
            settingDataSource.updateAPIUrl(platform.name, platform.apiUrl)
            settingDataSource.updateToken(platform.name, platform.token)
            settingDataSource.updateModel(platform.name, platform.model)
            settingDataSource.updateTemperature(platform.name, platform.temperature)
            settingDataSource.updateTopP(platform.name, platform.topP)
            settingDataSource.updateSystemPrompt(platform.name, platform.systemPrompt?.trim())
        }
    }

    override suspend fun updateThemes(themeSetting: ThemeSetting) {
        settingDataSource.updateDynamicTheme(themeSetting.dynamicTheme)
        settingDataSource.updateThemeMode(themeSetting.themeMode)
    }

    override suspend fun updateStreamingStyle(style: StreamingStyle) {
        settingDataSource.updateStreamingStyle(style)
    }

    override suspend fun updateWebDavConfig(config: WebDavConfig?) {
        settingDataSource.updateWebDavConfig(config)
    }

    override suspend fun updateSyncStatusSnapshot(snapshot: SyncStatusSnapshot?) {
        settingDataSource.updateSyncStatusSnapshot(snapshot)
    }
}
