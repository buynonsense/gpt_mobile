package dev.chungjungsoo.gptmobile.presentation.ui.mask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.AiMask
import dev.chungjungsoo.gptmobile.data.model.RoleDefaults
import dev.chungjungsoo.gptmobile.data.repository.AiMaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AiMaskListViewModel @Inject constructor(
    private val aiMaskRepository: AiMaskRepository
) : ViewModel() {

    data class UiState(
        val roles: List<AiMask> = emptyList(),
        val query: String = "",
        val isEditorOpen: Boolean = false,
        val editing: AiMask? = null,
        val editorName: String = "",
        val editorSystemPrompt: String = "",
        val editorGroupName: String = RoleDefaults.UNGROUPED_ROLE_NAME,
        val pendingArchive: AiMask? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val roles = aiMaskRepository.fetchAll()
            _uiState.update { it.copy(roles = roles) }
        }
    }

    fun updateQuery(q: String) {
        _uiState.update { it.copy(query = q) }
    }

    fun openCreate() {
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editing = null,
                editorName = "",
                editorSystemPrompt = "",
                editorGroupName = RoleDefaults.UNGROUPED_ROLE_NAME
            )
        }
    }

    fun openEdit(role: AiMask) {
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editing = role,
                editorName = role.name,
                editorSystemPrompt = role.systemPrompt,
                editorGroupName = role.groupName
            )
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(isEditorOpen = false, editing = null) }
    }

    fun updateEditorName(v: String) {
        _uiState.update { it.copy(editorName = v) }
    }

    fun updateEditorSystemPrompt(v: String) {
        _uiState.update { it.copy(editorSystemPrompt = v) }
    }

    fun updateEditorGroupName(v: String) {
        _uiState.update { it.copy(editorGroupName = v) }
    }

    fun saveEditor() {
        val name = _uiState.value.editorName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            aiMaskRepository.upsert(
                id = _uiState.value.editing?.id,
                name = name,
                systemPrompt = _uiState.value.editorSystemPrompt,
                groupName = _uiState.value.editorGroupName
            )
            _uiState.update { it.copy(isEditorOpen = false, editing = null, query = "") }
            refresh()
        }
    }

    fun requestArchive(role: AiMask) {
        _uiState.update { it.copy(pendingArchive = role) }
    }

    fun cancelArchive() {
        _uiState.update { it.copy(pendingArchive = null) }
    }

    fun confirmArchive() {
        val target = _uiState.value.pendingArchive ?: return
        viewModelScope.launch {
            aiMaskRepository.archive(target.id)
            _uiState.update { it.copy(pendingArchive = null) }
            refresh()
        }
    }

    fun filteredRoles(): List<AiMask> {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return _uiState.value.roles
        return _uiState.value.roles.filter {
            it.name.contains(q, ignoreCase = true) || it.groupName.contains(q, ignoreCase = true)
        }
    }
}
