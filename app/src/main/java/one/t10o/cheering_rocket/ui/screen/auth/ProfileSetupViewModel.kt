package one.t10o.cheering_rocket.ui.screen.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.data.repository.AuthRepository
import one.t10o.cheering_rocket.data.repository.StorageRepository
import javax.inject.Inject

/**
 * プロフィール設定画面のUI状態
 */
sealed class ProfileSetupUiState {
    data object Initial : ProfileSetupUiState()
    data object Loading : ProfileSetupUiState()
    data object UploadingImage : ProfileSetupUiState()
    data object Success : ProfileSetupUiState()
    data class Error(val message: String) : ProfileSetupUiState()
}

/**
 * プロフィール設定画面のViewModel
 */
@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileSetupUiState>(ProfileSetupUiState.Initial)
    val uiState: StateFlow<ProfileSetupUiState> = _uiState.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _photoUrl = MutableStateFlow<String?>(null)
    val photoUrl: StateFlow<String?> = _photoUrl.asStateFlow()

    init {
        // 現在のユーザー情報を取得して初期値を設定
        val currentUser = authRepository.getCurrentUser()
        currentUser?.displayName?.let { _userName.value = it }
        currentUser?.photoUrl?.let { _photoUrl.value = it }
    }

    /**
     * ユーザー名を更新
     */
    fun updateUserName(name: String) {
        _userName.value = name
    }

    /**
     * プロフィール画像をアップロード
     */
    fun uploadProfileImage(imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = ProfileSetupUiState.UploadingImage
            
            storageRepository.uploadProfileImage(imageUri)
                .onSuccess { url ->
                    _photoUrl.value = url
                    _uiState.value = ProfileSetupUiState.Initial
                }
                .onFailure { exception ->
                    _uiState.value = ProfileSetupUiState.Error(
                        exception.message ?: "画像のアップロードに失敗しました"
                    )
                }
        }
    }

    /**
     * プロフィール設定を完了
     */
    fun completeSetup() {
        if (_userName.value.isBlank()) {
            _uiState.value = ProfileSetupUiState.Error("ユーザー名を入力してください")
            return
        }

        viewModelScope.launch {
            _uiState.value = ProfileSetupUiState.Loading
            
            authRepository.completeProfileSetup(_userName.value, _photoUrl.value)
                .onSuccess {
                    _uiState.value = ProfileSetupUiState.Success
                }
                .onFailure { exception ->
                    _uiState.value = ProfileSetupUiState.Error(
                        exception.message ?: "プロフィール設定に失敗しました"
                    )
                }
        }
    }

    /**
     * UI状態をリセット
     */
    fun resetState() {
        _uiState.value = ProfileSetupUiState.Initial
    }
}
