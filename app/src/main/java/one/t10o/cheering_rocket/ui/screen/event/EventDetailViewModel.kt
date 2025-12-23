package one.t10o.cheering_rocket.ui.screen.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.data.model.Event
import one.t10o.cheering_rocket.data.model.EventInvitation
import one.t10o.cheering_rocket.data.model.InvitationStatus
import one.t10o.cheering_rocket.data.repository.EventRepository
import javax.inject.Inject

/**
 * イベント詳細画面のUI状態
 */
data class EventDetailUiState(
    val event: Event? = null,
    val members: List<EventInvitation> = emptyList(),
    val myInvitation: EventInvitation? = null,
    val isLoading: Boolean = true,
    val isOwner: Boolean = false,
    val isMember: Boolean = false,
    val isPendingInvitation: Boolean = false,
    val isProcessing: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val shareUrl: String
        get() = event?.let { "https://cheering-rocket.web.app/cheer/${it.shareToken}" } ?: ""
}

/**
 * イベント詳細画面のViewModel
 */
@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository
) : ViewModel() {
    
    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""
    
    private val _uiState = MutableStateFlow(EventDetailUiState())
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()
    
    init {
        loadEventDetail()
        loadMembers()
        loadMyInvitation()
    }
    
    private fun loadEventDetail() {
        viewModelScope.launch {
            eventRepository.observeEvent(eventId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
                .collect { event ->
                    val currentUserId = eventRepository.getCurrentUserId()
                    _uiState.value = _uiState.value.copy(
                        event = event,
                        isLoading = false,
                        isOwner = event?.ownerId == currentUserId
                    )
                }
        }
    }
    
    private fun loadMembers() {
        viewModelScope.launch {
            eventRepository.getEventMembers(eventId)
                .catch { /* エラーは無視 */ }
                .collect { members ->
                    _uiState.value = _uiState.value.copy(members = members)
                }
        }
    }
    
    private fun loadMyInvitation() {
        viewModelScope.launch {
            eventRepository.getMyInvitation(eventId)
                .onSuccess { invitation ->
                    _uiState.value = _uiState.value.copy(
                        myInvitation = invitation,
                        isMember = invitation?.status == InvitationStatus.ACCEPTED,
                        isPendingInvitation = invitation?.status == InvitationStatus.PENDING
                    )
                }
                .onFailure { /* エラーは無視 */ }
        }
    }
    
    /**
     * 招待を承認
     */
    fun acceptInvitation() {
        val invitation = _uiState.value.myInvitation ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            
            eventRepository.acceptInvitation(invitation.id)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        isMember = true,
                        isPendingInvitation = false,
                        successMessage = "イベントに参加しました"
                    )
                    loadMyInvitation()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: "承認に失敗しました"
                    )
                }
        }
    }
    
    /**
     * 招待を拒否
     */
    fun rejectInvitation() {
        val invitation = _uiState.value.myInvitation ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            
            eventRepository.rejectInvitation(invitation.id)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        isPendingInvitation = false,
                        successMessage = "招待を拒否しました"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: "拒否に失敗しました"
                    )
                }
        }
    }
    
    /**
     * イベントから脱退
     */
    fun leaveEvent() {
        val invitation = _uiState.value.myInvitation ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            
            eventRepository.leaveEvent(invitation.id)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        isMember = false,
                        myInvitation = null,
                        successMessage = "イベントから脱退しました"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: "脱退に失敗しました"
                    )
                }
        }
    }
    
    /**
     * イベントを削除
     */
    fun deleteEvent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            
            eventRepository.deleteEvent(eventId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        isDeleted = true,
                        successMessage = "イベントを削除しました"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: "削除に失敗しました"
                    )
                }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}

