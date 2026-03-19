package dev.chungjungsoo.gptmobile.presentation.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.projection.MessageSearchResult
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MessageSearchViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val results: List<MessageSearchResult> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    fun updateQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            _uiState.update { state ->
                state.copy(results = chatRepository.searchMessages(value))
            }
        }
    }
}
