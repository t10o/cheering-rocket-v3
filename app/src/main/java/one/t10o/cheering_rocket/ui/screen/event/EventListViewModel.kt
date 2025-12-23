package one.t10o.cheering_rocket.ui.screen.event

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
import one.t10o.cheering_rocket.data.repository.EventRepository
import javax.inject.Inject

/**
 * イベント一覧画面のUI状態
 */
data class EventListUiState(
    val myEvents: List<Event> = emptyList(),
    val pendingInvitations: List<EventInvitation> = emptyList(),
    val joinedEvents: List<EventInvitation> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/**
 * イベント一覧画面のViewModel
 */
@HiltViewModel
class EventListViewModel @Inject constructor(
    private val eventRepository: EventRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EventListUiState())
    val uiState: StateFlow<EventListUiState> = _uiState.asStateFlow()
    
    init {
        loadMyEvents()
        loadPendingInvitations()
        loadJoinedEvents()
    }
    
    private fun loadMyEvents() {
        viewModelScope.launch {
            eventRepository.getMyEvents()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = e.message
                    )
                }
                .collect { events ->
                    _uiState.value = _uiState.value.copy(
                        myEvents = events,
                        isLoading = false
                    )
                }
        }
    }
    
    private fun loadPendingInvitations() {
        viewModelScope.launch {
            eventRepository.getPendingInvitations()
                .catch { /* エラーは無視 */ }
                .collect { invitations ->
                    _uiState.value = _uiState.value.copy(
                        pendingInvitations = invitations
                    )
                }
        }
    }
    
    private fun loadJoinedEvents() {
        viewModelScope.launch {
            eventRepository.getJoinedEvents()
                .catch { /* エラーは無視 */ }
                .collect { invitations ->
                    _uiState.value = _uiState.value.copy(
                        joinedEvents = invitations
                    )
                }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

