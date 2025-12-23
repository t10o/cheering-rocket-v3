package one.t10o.cheering_rocket.ui.screen.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.data.model.UserProfile
import one.t10o.cheering_rocket.data.repository.FriendRepository
import javax.inject.Inject

/**
 * フレンド検索画面のUI状態
 */
data class FriendSearchUiState(
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResult: UserProfile? = null,
    val hasSearched: Boolean = false,
    val isSendingRequest: Boolean = false,
    val requestSent: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

/**
 * フレンド検索画面のViewModel
 */
@HiltViewModel
class FriendSearchViewModel @Inject constructor(
    private val friendRepository: FriendRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(FriendSearchUiState())
    val uiState: StateFlow<FriendSearchUiState> = _uiState.asStateFlow()
    
    /**
     * 検索クエリを更新
     */
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            errorMessage = null
        )
    }
    
    /**
     * アカウントIDでユーザーを検索
     */
    fun searchUser() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "アカウントIDを入力してください"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                errorMessage = null,
                searchResult = null,
                hasSearched = false,
                requestSent = false
            )
            
            friendRepository.searchUserByAccountId(query)
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        searchResult = user,
                        hasSearched = true,
                        errorMessage = if (user == null) "ユーザーが見つかりませんでした" else null
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        hasSearched = true,
                        errorMessage = e.message ?: "検索に失敗しました"
                    )
                }
        }
    }
    
    /**
     * フレンド申請を送信
     */
    fun sendFriendRequest() {
        val user = _uiState.value.searchResult ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSendingRequest = true,
                errorMessage = null,
                successMessage = null
            )
            
            friendRepository.sendFriendRequest(user)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSendingRequest = false,
                        requestSent = true,
                        successMessage = "${user.displayName}さんにフレンド申請を送信しました"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSendingRequest = false,
                        errorMessage = e.message ?: "フレンド申請の送信に失敗しました"
                    )
                }
        }
    }
    
    /**
     * 検索をリセット
     */
    fun resetSearch() {
        _uiState.value = FriendSearchUiState()
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

