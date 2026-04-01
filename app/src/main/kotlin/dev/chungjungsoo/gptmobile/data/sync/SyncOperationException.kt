package dev.chungjungsoo.gptmobile.data.sync

import dev.chungjungsoo.gptmobile.data.sync.model.SyncErrorCategory

class SyncOperationException(
    val category: SyncErrorCategory,
    cause: Throwable? = null
) : IllegalStateException(category.name, cause)
