package dev.chungjungsoo.gptmobile.data.model

import dev.chungjungsoo.gptmobile.data.database.entity.AiMask

data class RoleGroup(
    val groupName: String,
    val roles: List<AiMask>
)
