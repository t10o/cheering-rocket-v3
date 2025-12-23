package one.t10o.cheering_rocket.ui.screen.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.data.model.Friendship
import one.t10o.cheering_rocket.data.repository.FriendRepository
import javax.inject.Inject

/**
 * フレンド一覧画面のUI状態
 */
data class FriendListUiState(
    val friends: List<Friendship> = emptyList(),
    val isLoading: Boolean = true,
    val pendingRequestCount: Int = 0,
    val deletingFriendshipId: String? = null,
    val friendToDelete: Friendship? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

/**
 * フレンド一覧画面のViewModel
 */
@HiltViewModel
class FriendListViewModel @Inject constructor(
    private val friendRepository: FriendRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(FriendListUiState())
    val uiState: StateFlow<FriendListUiState> = _uiState.asStateFlow()
    
    init {
        loadFriends()
        loadPendingRequestCount()
    }
    
    /**
     * フレンド一覧を読み込み
     */
    private fun loadFriends() {
        viewModelScope.launch {
            friendRepository.getFriends()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "フレンドの読み込みに失敗しました"
                    )
                }
                .collect { friends ->
                    _uiState.value = _uiState.value.copy(
                        friends = friends,
                        isLoading = false
                    )
                }
        }
    }
    
    /**
     * 保留中の申請数を読み込み
     */
    private fun loadPendingRequestCount() {
        viewModelScope.launch {
            friendRepository.getPendingRequestCount()
                .catch { /* エラーは無視 */ }
                .collect { count ->
                    _uiState.value = _uiState.value.copy(pendingRequestCount = count)
                }
        }
    }
    
    /**
     * 削除確認ダイアログを表示
     */
    fun showDeleteConfirmation(friendship: Friendship) {
        _uiState.value = _uiState.value.copy(friendToDelete = friendship)
    }
    
    /**
     * 削除確認ダイアログを閉じる
     */
    fun dismissDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(friendToDelete = null)
    }
    
    /**
     * フレンドを削除
     */
    fun deleteFriend() {
        val friendship = _uiState.value.friendToDelete ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                deletingFriendshipId = friendship.id,
                friendToDelete = null,
                errorMessage = null
            )
            
            friendRepository.deleteFriendship(friendship)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        deletingFriendshipId = null,
                        successMessage = "${friendship.friendUserName}さんをフレンドから削除しました"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        deletingFriendshipId = null,
                        errorMessage = e.message ?: "削除に失敗しました"
                    )
                }
        }
    }
    
    /**
     * エラーメッセージをクリア
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * 成功メッセージをクリア
     */
    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
