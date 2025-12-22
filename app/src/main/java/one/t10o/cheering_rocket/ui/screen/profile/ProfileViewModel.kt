package one.t10o.cheering_rocket.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.data.repository.AuthRepository
import javax.inject.Inject

/**
 * プロフィール画面のUI状態
 */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val accountId: String? = null,
    val error: String? = null
)

/**
 * プロフィール画面のViewModel
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    /**
     * プロフィール情報を読み込む
     */
    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            // Firebase Authのユーザー情報を取得
            val currentUser = authRepository.getCurrentUser()
            
            // Firestoreからプロフィール情報を取得
            authRepository.getUserProfile()
                .onSuccess { profile ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        displayName = profile["displayName"] as? String 
                            ?: currentUser?.displayName 
                            ?: "名前未設定",
                        email = currentUser?.email,
                        photoUrl = profile["photoUrl"] as? String 
                            ?: currentUser?.photoUrl,
                        accountId = profile["accountId"] as? String,
                        error = null
                    )
                }
                .onFailure { exception ->
                    // Firestoreから取得できない場合はFirebase Authの情報を使用
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        displayName = currentUser?.displayName ?: "名前未設定",
                        email = currentUser?.email,
                        photoUrl = currentUser?.photoUrl,
                        accountId = null,
                        error = null
                    )
                }
        }
    }
}

