package one.t10o.cheering_rocket.ui.screen.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.data.model.User
import one.t10o.cheering_rocket.data.repository.AuthRepository
import javax.inject.Inject

/**
 * 認証画面のUI状態
 */
sealed class AuthUiState {
    data object Initial : AuthUiState()
    data object Loading : AuthUiState()
    data class Success(val user: User, val isNewUser: Boolean) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

/**
 * 認証関連のViewModel
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val googleSignInClient: GoogleSignInClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Initial)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(authRepository.getCurrentUser() != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        // 認証状態を監視
        viewModelScope.launch {
            authRepository.authState.collect { user ->
                _isLoggedIn.value = user != null
            }
        }
    }

    /**
     * 現在のユーザーを取得
     */
    fun getCurrentUser(): User? {
        return authRepository.getCurrentUser()
    }

    /**
     * プロフィール設定が完了しているか確認
     */
    suspend fun isProfileCompleted(): Boolean {
        return authRepository.isProfileCompleted()
    }

    /**
     * Google認証の結果を処理
     */
    fun handleGoogleSignInResult(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            authRepository.signInWithGoogle(account)
                .onSuccess { user ->
                    _uiState.value = AuthUiState.Success(user, user.isNewUser)
                }
                .onFailure { exception ->
                    _uiState.value = AuthUiState.Error(
                        exception.message ?: "サインインに失敗しました"
                    )
                }
        }
    }

    /**
     * サインイン失敗を処理
     */
    fun handleSignInError(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    /**
     * UI状態をリセット
     */
    fun resetState() {
        _uiState.value = AuthUiState.Initial
    }

    /**
     * サインアウト
     */
    fun signOut() {
        authRepository.signOut()
        googleSignInClient.signOut()
        _uiState.value = AuthUiState.Initial
    }
}

