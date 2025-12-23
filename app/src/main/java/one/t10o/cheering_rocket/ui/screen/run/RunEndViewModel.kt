package one.t10o.cheering_rocket.ui.screen.run

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.data.model.RunSession
import one.t10o.cheering_rocket.data.model.RunStatus
import one.t10o.cheering_rocket.data.repository.RunRepository
import one.t10o.cheering_rocket.service.LocationForegroundService
import javax.inject.Inject

/**
 * ラン終了画面のUI状態
 */
data class RunEndUiState(
    val runSession: RunSession? = null,
    val isLoading: Boolean = true,
    val isFinishing: Boolean = false,
    val isFinished: Boolean = false,
    val errorMessage: String? = null
) {
    val formattedDistance: String
        get() {
            val meters = runSession?.totalDistanceMeters ?: 0.0
            return if (meters >= 1000) {
                String.format("%.2f", meters / 1000)
            } else {
                String.format("%.0f", meters)
            }
        }
    
    val distanceUnit: String
        get() {
            val meters = runSession?.totalDistanceMeters ?: 0.0
            return if (meters >= 1000) "km" else "m"
        }
    
    val formattedDuration: String
        get() {
            val start = runSession?.startedAt?.toDate()?.time ?: return "00:00:00"
            val now = System.currentTimeMillis()
            val durationMs = now - start
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / (1000 * 60)) % 60
            val hours = durationMs / (1000 * 60 * 60)
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    
    val formattedAveragePace: String
        get() {
            val meters = runSession?.totalDistanceMeters ?: 0.0
            val start = runSession?.startedAt?.toDate()?.time ?: return "--:--"
            val now = System.currentTimeMillis()
            val durationMs = now - start
            
            if (meters < 10) return "--:--"  // 10m未満はペース計算しない
            
            val km = meters / 1000.0
            val minutes = durationMs / (1000.0 * 60)
            val paceMinPerKm = minutes / km
            
            val paceMin = paceMinPerKm.toInt()
            val paceSec = ((paceMinPerKm - paceMin) * 60).toInt()
            
            return String.format("%d:%02d", paceMin, paceSec)
        }
}

/**
 * ラン終了画面のViewModel
 */
@HiltViewModel
class RunEndViewModel @Inject constructor(
    private val runRepository: RunRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""
    
    private val _uiState = MutableStateFlow(RunEndUiState())
    val uiState: StateFlow<RunEndUiState> = _uiState.asStateFlow()
    
    init {
        loadCurrentRun()
    }
    
    private fun loadCurrentRun() {
        viewModelScope.launch {
            // まず現在のランを取得
            runRepository.getCurrentRun(eventId)
                .onSuccess { session ->
                    if (session != null) {
                        // runIdでリアルタイム監視
                        runRepository.observeRun(session.id)
                            .catch { e ->
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    errorMessage = e.message
                                )
                            }
                            .collect { updatedSession ->
                                _uiState.value = _uiState.value.copy(
                                    runSession = updatedSession,
                                    isLoading = false
                                )
                            }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "走行中のランが見つかりません"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
        }
    }
    
    /**
     * ランを終了する
     */
    fun finishRun(confirmText: String) {
        if (confirmText != "終了") {
            _uiState.value = _uiState.value.copy(
                errorMessage = "「終了」と入力してください"
            )
            return
        }
        
        val runId = _uiState.value.runSession?.id
        if (runId == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "ランが見つかりません"
            )
            return
        }
        
        _uiState.value = _uiState.value.copy(isFinishing = true)
        
        viewModelScope.launch {
            // Foreground Serviceを停止
            stopLocationService()
            
            // Firestoreのランを終了
            runRepository.finishRun(runId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isFinishing = false,
                        isFinished = true
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isFinishing = false,
                        errorMessage = "終了に失敗しました: ${e.message}"
                    )
                }
        }
    }
    
    private fun stopLocationService() {
        val intent = Intent(context, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

