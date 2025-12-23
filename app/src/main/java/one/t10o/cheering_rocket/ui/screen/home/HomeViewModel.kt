package one.t10o.cheering_rocket.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.data.model.Event
import one.t10o.cheering_rocket.data.model.EventInvitation
import one.t10o.cheering_rocket.data.model.RunSession
import one.t10o.cheering_rocket.data.model.RunStatus
import one.t10o.cheering_rocket.data.repository.EventRepository
import one.t10o.cheering_rocket.data.repository.RunRepository
import javax.inject.Inject

/**
 * ホーム画面に表示するイベント情報（ランの状態含む）
 */
data class HomeEventItem(
    val event: Event,
    val activeRun: RunSession? = null,  // 自分の走行中ラン
    val isOwner: Boolean = false
)

/**
 * ホーム画面のUI状態
 */
data class HomeUiState(
    val userName: String = "",
    val activeRuns: List<RunSession> = emptyList(),  // 現在走行中のラン
    val upcomingEvents: List<HomeEventItem> = emptyList(),  // 開催前/開催中のイベント
    val pendingInvitations: List<EventInvitation> = emptyList(),  // 保留中の招待
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val hasActiveRun: Boolean
        get() = activeRuns.isNotEmpty()
    
    val pendingInvitationCount: Int
        get() = pendingInvitations.size
}

/**
 * ホーム画面のViewModel
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val runRepository: RunRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadHomeData()
    }
    
    private fun loadHomeData() {
        viewModelScope.launch {
            // 複数のFlowを組み合わせて監視
            combine(
                eventRepository.getMyEvents(),
                eventRepository.getJoinedEvents(),
                eventRepository.getPendingInvitations()
            ) { myEvents, joinedInvitations, pendingInvitations ->
                Triple(myEvents, joinedInvitations, pendingInvitations)
            }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
                .collect { (myEvents, joinedInvitations, pendingInvitations) ->
                    // 自分が作成したイベント
                    val myEventItems = myEvents.map { event ->
                        HomeEventItem(
                            event = event,
                            isOwner = true
                        )
                    }
                    
                    // 参加中のイベント（招待を受けて承認済み）
                    val joinedEventItems = joinedInvitations.mapNotNull { invitation ->
                        // イベント情報を取得
                        eventRepository.getEvent(invitation.eventId).getOrNull()?.let { event ->
                            HomeEventItem(
                                event = event,
                                isOwner = false
                            )
                        }
                    }
                    
                    // 重複を除いてマージ（自分のイベントを優先）
                    val allEvents = (myEventItems + joinedEventItems)
                        .distinctBy { it.event.id }
                        .sortedByDescending { it.event.startDateTime?.toDate()?.time ?: 0 }
                    
                    _uiState.value = _uiState.value.copy(
                        upcomingEvents = allEvents,
                        pendingInvitations = pendingInvitations,
                        isLoading = false
                    )
                }
        }
        
        // 走行中のランを監視（独立してリアルタイム監視）
        observeActiveRuns()
    }
    
    private fun observeActiveRuns() {
        viewModelScope.launch {
            runRepository.observeMyActiveRuns()
                .catch { /* エラーは無視 */ }
                .collect { runs ->
                    _uiState.value = _uiState.value.copy(activeRuns = runs)
                }
        }
    }
    
    /**
     * データを再読み込み
     */
    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadHomeData()
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

