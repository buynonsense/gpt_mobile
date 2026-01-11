package dev.chungjungsoo.gptmobile.data.dto.anthropic.response

import kotlinx.serialization.Serializable

@Serializable
data class ModelListResponse(
    val data: List<ModelData> = emptyList()
)

@Serializable
data class ModelData(
    val id: String
)
