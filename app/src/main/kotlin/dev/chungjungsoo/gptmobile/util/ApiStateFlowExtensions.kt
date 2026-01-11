package dev.chungjungsoo.gptmobile.util

import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

suspend fun Flow<ApiState>.handleStates(
    messageFlow: MutableStateFlow<Message>,
    onLoadingComplete: () -> Unit,
    onTextChunk: ((String) -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null
) = collect { chunk ->
    when (chunk) {
        is ApiState.Success -> {
            if (onTextChunk != null) {
                onTextChunk(chunk.textChunk)
            } else {
                messageFlow.addContent(chunk.textChunk)
            }
        }
        ApiState.Done -> {
            if (onDone != null) {
                onDone()
            } else {
                messageFlow.setTimestamp()
            }
            onLoadingComplete()
        }

        is ApiState.Error -> {
            if (onError != null) {
                onError(chunk.message)
            } else {
                messageFlow.setErrorMessage(chunk.message)
            }
            onLoadingComplete()
        }

        else -> {}
    }
}

private fun MutableStateFlow<Message>.addContent(text: String) = update { it.copy(content = it.content + text) }

private fun MutableStateFlow<Message>.setErrorMessage(error: String) = update { it.copy(content = "Error: $error", createdAt = System.currentTimeMillis() / 1000) }

private fun MutableStateFlow<Message>.setTimestamp() = update { it.copy(createdAt = System.currentTimeMillis() / 1000) }
