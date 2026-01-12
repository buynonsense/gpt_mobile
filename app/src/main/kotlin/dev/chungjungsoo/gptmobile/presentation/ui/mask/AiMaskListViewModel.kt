package dev.chungjungsoo.gptmobile.presentation.ui.mask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.AiMask
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.repository.AiMaskRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AiMaskListViewModel @Inject constructor(
    private val aiMaskRepository: AiMaskRepository,
    private val settingRepository: SettingRepository
) : ViewModel() {

    data class UiState(
        val masks: List<AiMask> = emptyList(),
        val platformState: List<Platform> = emptyList(),
        val query: String = "",
        val isEditorOpen: Boolean = false,
        val editing: AiMask? = null,
        val editorName: String = "",
        val editorSystemPrompt: String = "",
        val pendingDelete: AiMask? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val masks = aiMaskRepository.fetchAll()
            val platforms = settingRepository.fetchPlatforms()
            _uiState.update { it.copy(masks = masks, platformState = platforms) }
        }
    }

    fun touchMask(maskId: Int) {
        viewModelScope.launch {
            aiMaskRepository.touch(maskId)
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
                editorSystemPrompt = ""
            )
        }
    }

    fun openEdit(mask: AiMask) {
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editing = mask,
                editorName = mask.name,
                editorSystemPrompt = mask.systemPrompt
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

    fun saveEditor() {
        val name = _uiState.value.editorName.trim()
        val prompt = _uiState.value.editorSystemPrompt.trim()
        if (name.isBlank() || prompt.isBlank()) return

        viewModelScope.launch {
            val editingId = _uiState.value.editing?.id
            val saved = aiMaskRepository.upsert(id = editingId, name = name, systemPrompt = prompt)

            _uiState.update {
                val nextMasks = listOf(saved) + it.masks.filter { m -> m.id != saved.id }
                it.copy(
                    masks = nextMasks,
                    query = "",
                    isEditorOpen = false,
                    editing = null
                )
            }

            refresh()
        }
    }

    fun requestDelete(mask: AiMask) {
        _uiState.update { it.copy(pendingDelete = mask) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val target = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            aiMaskRepository.delete(target.id)
            _uiState.update { it.copy(pendingDelete = null) }
            refresh()
        }
    }

    fun filteredMasks(): List<AiMask> {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return _uiState.value.masks
        return _uiState.value.masks.filter { it.name.contains(q, ignoreCase = true) }
    }
}
