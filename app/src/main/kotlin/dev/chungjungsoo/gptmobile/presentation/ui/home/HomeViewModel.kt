package dev.chungjungsoo.gptmobile.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.model.RoleGroup
import dev.chungjungsoo.gptmobile.data.repository.AiMaskRepository
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiMaskRepository: AiMaskRepository
) : ViewModel() {

    data class UiState(
        val roleGroups: List<RoleGroup> = emptyList(),
        val isLoading: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val defaultRole = aiMaskRepository.fetchDefault()
            val groupedRoles = aiMaskRepository.fetchGroupedActive()
            val mergedGroups = groupedRoles.toMutableList()
            if (mergedGroups.none { group -> group.roles.any { it.id == defaultRole.id } }) {
                mergedGroups.add(0, RoleGroup(groupName = defaultRole.groupName, roles = listOf(defaultRole)))
            }
            _uiState.update {
                it.copy(
                    roleGroups = mergedGroups.sortedBy { group -> if (group.groupName == defaultRole.groupName) 0 else 1 },
                    isLoading = false
                )
            }
        }
    }

    fun openRole(roleId: Int, onResolved: (ChatRoom) -> Unit) {
        viewModelScope.launch {
            val chatRoom = chatRepository.findOrCreateChatForRole(roleId)
            onResolved(chatRoom)
            refresh()
        }
    }
}
