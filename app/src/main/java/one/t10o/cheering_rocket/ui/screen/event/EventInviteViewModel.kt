package one.t10o.cheering_rocket.ui.screen.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.data.model.Friendship
import one.t10o.cheering_rocket.data.repository.EventRepository
import one.t10o.cheering_rocket.data.repository.FriendRepository
import javax.inject.Inject

/**
 * 招待可能なフレンド情報
 */
data class InvitableFriend(
    val friendship: Friendship,
    val isSelected: Boolean = false,
    val isAlreadyInvited: Boolean = false
)

/**
 * イベント招待画面のUI状態
 */
data class EventInviteUiState(
    val friends: List<InvitableFriend> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val invitesSent: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val selectedCount: Int
        get() = friends.count { it.isSelected }
    
    val hasSelectableFriends: Boolean
        get() = friends.any { !it.isAlreadyInvited }
}

/**
 * イベント招待画面のViewModel
 */
@HiltViewModel
class EventInviteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository,
    private val friendRepository: FriendRepository
) : ViewModel() {
    
    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""
    
    private val _uiState = MutableStateFlow(EventInviteUiState())
    val uiState: StateFlow<EventInviteUiState> = _uiState.asStateFlow()
    
    init {
        loadFriendsAndInvitations()
    }
    
    private fun loadFriendsAndInvitations() {
        viewModelScope.launch {
            try {
                // フレンド一覧を取得
                val friends = friendRepository.getFriends().first()
                
                // 既に招待済みのユーザーID一覧を取得
                val invitedUserIds = eventRepository.getInvitedUserIds(eventId)
                    .getOrDefault(emptyList())
                
                val invitableFriends = friends.map { friendship ->
                    InvitableFriend(
                        friendship = friendship,
                        isSelected = false,
                        isAlreadyInvited = invitedUserIds.contains(friendship.friendUserId)
                    )
                }
                
                _uiState.value = _uiState.value.copy(
                    friends = invitableFriends,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "フレンドの読み込みに失敗しました"
                )
            }
        }
    }
    
    /**
     * フレンドの選択状態を切り替え
     */
    fun toggleFriendSelection(friendUserId: String) {
        val currentFriends = _uiState.value.friends.toMutableList()
        val index = currentFriends.indexOfFirst { it.friendship.friendUserId == friendUserId }
        
        if (index >= 0) {
            val friend = currentFriends[index]
            if (!friend.isAlreadyInvited) {
                currentFriends[index] = friend.copy(isSelected = !friend.isSelected)
                _uiState.value = _uiState.value.copy(friends = currentFriends)
            }
        }
    }
    
    /**
     * 選択したフレンドに招待を送信
     */
    fun sendInvitations() {
        val selectedFriends = _uiState.value.friends.filter { it.isSelected }
        if (selectedFriends.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "招待するフレンドを選択してください"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSending = true,
                errorMessage = null
            )
            
            var successCount = 0
            var failCount = 0
            
            for (friend in selectedFriends) {
                eventRepository.inviteToEvent(
                    eventId = eventId,
                    friendUserId = friend.friendship.friendUserId,
                    friendUserName = friend.friendship.friendUserName
                )
                    .onSuccess { successCount++ }
                    .onFailure { failCount++ }
            }
            
            if (successCount > 0) {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    invitesSent = true,
                    successMessage = "${successCount}人に招待を送信しました"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = "招待の送信に失敗しました"
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

