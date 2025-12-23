package one.t10o.cheering_rocket.ui.screen.run

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import one.t10o.cheering_rocket.data.repository.RunRepository
import java.util.UUID
import javax.inject.Inject

/**
 * 写真撮影画面のUI状態
 */
data class PhotoCaptureUiState(
    val isUploading: Boolean = false,
    val isUploaded: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 写真撮影画面のViewModel
 */
@HiltViewModel
class PhotoCaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val runRepository: RunRepository,
    private val firebaseStorage: FirebaseStorage
) : ViewModel() {
    
    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""
    private val runId: String? = savedStateHandle.get<String>("runId")
    
    private val _uiState = MutableStateFlow(PhotoCaptureUiState())
    val uiState: StateFlow<PhotoCaptureUiState> = _uiState.asStateFlow()
    
    // 現在の位置情報（RunViewModelから取得するか、直接取得する）
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    
    init {
        // 現在のランセッションを取得
        loadCurrentRun()
    }
    
    private fun loadCurrentRun() {
        viewModelScope.launch {
            runRepository.getCurrentRun(eventId)
                .onSuccess { session ->
                    session?.latestLocation?.let { location ->
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                    }
                }
        }
    }
    
    /**
     * 写真をアップロード
     */
    fun uploadPhoto(imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null)
            
            try {
                // 現在のランセッションを取得
                val currentRun = runRepository.getCurrentRun(eventId).getOrNull()
                    ?: throw Exception("ランセッションが見つかりません")
                
                val runSessionId = currentRun.id
                
                // 位置情報を更新
                currentRun.latestLocation?.let { location ->
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                }
                
                // Storageにアップロード
                val fileName = "photo_${UUID.randomUUID()}.jpg"
                val storagePath = "runs/$runSessionId/photos/$fileName"
                val storageRef = firebaseStorage.reference.child(storagePath)
                
                // アップロード
                val inputStream = application.contentResolver.openInputStream(imageUri)
                    ?: throw Exception("画像を読み込めませんでした")
                
                storageRef.putStream(inputStream).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()
                
                // Firestoreに保存
                runRepository.savePhoto(
                    runId = runSessionId,
                    storagePath = storagePath,
                    downloadUrl = downloadUrl,
                    latitude = currentLatitude,
                    longitude = currentLongitude
                )
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            isUploaded = true
                        )
                    }
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            errorMessage = e.message ?: "保存に失敗しました"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    errorMessage = e.message ?: "アップロードに失敗しました"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

