package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.StreamingStyle
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig

interface SettingRepository {
    suspend fun fetchPlatforms(): List<Platform>
    suspend fun fetchThemes(): ThemeSetting
    suspend fun fetchStreamingStyle(): StreamingStyle
    suspend fun fetchWebDavConfig(): WebDavConfig?
    suspend fun updatePlatforms(platforms: List<Platform>)
    suspend fun updateThemes(themeSetting: ThemeSetting)
    suspend fun updateStreamingStyle(style: StreamingStyle)
    suspend fun updateWebDavConfig(config: WebDavConfig?)
}
