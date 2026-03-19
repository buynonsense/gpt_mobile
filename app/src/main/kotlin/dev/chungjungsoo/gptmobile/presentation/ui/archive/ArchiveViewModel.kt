package dev.chungjungsoo.gptmobile.presentation.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.AiMask
import dev.chungjungsoo.gptmobile.data.repository.AiMaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val aiMaskRepository: AiMaskRepository
) : ViewModel() {

    data class UiState(
        val archivedRoles: List<AiMask> = emptyList(),
        val pendingDelete: AiMask? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(archivedRoles = aiMaskRepository.fetchArchived()) }
        }
    }

    fun restore(roleId: Int) {
        viewModelScope.launch {
            aiMaskRepository.restore(roleId)
            refresh()
        }
    }

    fun requestDelete(role: AiMask) {
        _uiState.update { it.copy(pendingDelete = role) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val target = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            aiMaskRepository.deletePermanently(target.id)
            _uiState.update { it.copy(pendingDelete = null) }
            refresh()
        }
    }
}
