package one.t10o.cheering_rocket.ui.screen.profile

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
 * プロフィール編集画面のUI状態
 */
sealed class ProfileEditUiState {
    data object Initial : ProfileEditUiState()
    data object Loading : ProfileEditUiState()
    data object UploadingImage : ProfileEditUiState()
    data object Success : ProfileEditUiState()
    data class Error(val message: String) : ProfileEditUiState()
}

/**
 * プロフィール編集画面のViewModel
 */
@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileEditUiState>(ProfileEditUiState.Initial)
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _photoUrl = MutableStateFlow<String?>(null)
    val photoUrl: StateFlow<String?> = _photoUrl.asStateFlow()

    init {
        loadProfile()
    }

    /**
     * プロフィール情報を読み込む
     */
    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileEditUiState.Loading
            
            authRepository.getUserProfile()
                .onSuccess { profile ->
                    _userName.value = profile["displayName"] as? String ?: ""
                    _photoUrl.value = profile["photoUrl"] as? String
                    _uiState.value = ProfileEditUiState.Initial
                }
                .onFailure { exception ->
                    // Firestoreから取得できない場合はFirebase Authの情報を使用
                    val currentUser = authRepository.getCurrentUser()
                    _userName.value = currentUser?.displayName ?: ""
                    _photoUrl.value = currentUser?.photoUrl
                    _uiState.value = ProfileEditUiState.Initial
                }
        }
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
            _uiState.value = ProfileEditUiState.UploadingImage
            
            storageRepository.uploadProfileImage(imageUri)
                .onSuccess { url ->
                    _photoUrl.value = url
                    _uiState.value = ProfileEditUiState.Initial
                }
                .onFailure { exception ->
                    _uiState.value = ProfileEditUiState.Error(
                        exception.message ?: "画像のアップロードに失敗しました"
                    )
                }
        }
    }

    /**
     * プロフィールを保存
     */
    fun saveProfile() {
        if (_userName.value.isBlank()) {
            _uiState.value = ProfileEditUiState.Error("ユーザー名を入力してください")
            return
        }

        viewModelScope.launch {
            _uiState.value = ProfileEditUiState.Loading
            
            authRepository.completeProfileSetup(_userName.value, _photoUrl.value)
                .onSuccess {
                    _uiState.value = ProfileEditUiState.Success
                }
                .onFailure { exception ->
                    _uiState.value = ProfileEditUiState.Error(
                        exception.message ?: "プロフィールの保存に失敗しました"
                    )
                }
        }
    }

    /**
     * UI状態をリセット
     */
    fun resetState() {
        _uiState.value = ProfileEditUiState.Initial
    }
}

