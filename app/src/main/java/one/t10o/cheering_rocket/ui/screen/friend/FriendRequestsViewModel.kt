package one.t10o.cheering_rocket.ui.screen.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.data.model.FriendRequest
import one.t10o.cheering_rocket.data.repository.FriendRepository
import javax.inject.Inject

/**
 * フレンド申請一覧画面のUI状態
 */
data class FriendRequestsUiState(
    val requests: List<FriendRequest> = emptyList(),
    val isLoading: Boolean = true,
    val processingRequestId: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

/**
 * フレンド申請一覧画面のViewModel
 */
@HiltViewModel
class FriendRequestsViewModel @Inject constructor(
    private val friendRepository: FriendRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(FriendRequestsUiState())
    val uiState: StateFlow<FriendRequestsUiState> = _uiState.asStateFlow()
    
    init {
        loadFriendRequests()
    }
    
    /**
     * フレンド申請一覧を読み込み（リアルタイム更新）
     */
    private fun loadFriendRequests() {
        viewModelScope.launch {
            friendRepository.getReceivedFriendRequests()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "申請の読み込みに失敗しました"
                    )
                }
                .collect { requests ->
                    _uiState.value = _uiState.value.copy(
                        requests = requests,
                        isLoading = false
                    )
                }
        }
    }
    
    /**
     * フレンド申請を承認
     */
    fun acceptRequest(request: FriendRequest) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                processingRequestId = request.id,
                errorMessage = null
            )
            
            friendRepository.acceptFriendRequest(request)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        processingRequestId = null,
                        successMessage = "${request.fromUserName}さんとフレンドになりました"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        processingRequestId = null,
                        errorMessage = e.message ?: "承認に失敗しました"
                    )
                }
        }
    }
    
    /**
     * フレンド申請を拒否
     */
    fun rejectRequest(request: FriendRequest) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                processingRequestId = request.id,
                errorMessage = null
            )
            
            friendRepository.rejectFriendRequest(request)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        processingRequestId = null,
                        successMessage = "申請を拒否しました"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        processingRequestId = null,
                        errorMessage = e.message ?: "拒否に失敗しました"
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

