package one.t10o.cheering_rocket.ui.screen.run

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.IBinder
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.data.model.CheerMessage
import one.t10o.cheering_rocket.data.model.RunPhoto
import one.t10o.cheering_rocket.data.model.RunSession
import one.t10o.cheering_rocket.data.model.RunStatus
import one.t10o.cheering_rocket.data.repository.RunRepository
import one.t10o.cheering_rocket.service.LocationForegroundService
import javax.inject.Inject

/**
 * ラン画面のUI状態
 */
data class RunUiState(
    val runSession: RunSession? = null,
    val isLoading: Boolean = true,
    val isServiceBound: Boolean = false,
    val isTracking: Boolean = false,
    val lastLocation: Location? = null,
    val totalDistanceMeters: Double = 0.0,
    val durationSeconds: Long = 0,
    val currentPaceSecondsPerKm: Int? = null,  // ペース（秒/km）
    val averagePaceSecondsPerKm: Int? = null,  // 平均ペース
    val cheerMessages: List<CheerMessage> = emptyList(),
    val photos: List<RunPhoto> = emptyList(),
    val pendingLocationCount: Int = 0,
    val errorMessage: String? = null,
    val isFinishing: Boolean = false,
    val isFinished: Boolean = false
) {
    /**
     * 距離をフォーマット（km）
     */
    val formattedDistance: String
        get() = if (totalDistanceMeters >= 1000) {
            String.format("%.2f", totalDistanceMeters / 1000)
        } else {
            String.format("%.0f m", totalDistanceMeters)
        }
    
    /**
     * 距離の単位
     */
    val distanceUnit: String
        get() = if (totalDistanceMeters >= 1000) "km" else ""
    
    /**
     * 現在のペースをフォーマット（分:秒/km）
     */
    val formattedCurrentPace: String
        get() = currentPaceSecondsPerKm?.let { formatPace(it) } ?: "--:--"
    
    /**
     * 平均ペースをフォーマット
     */
    val formattedAveragePace: String
        get() = averagePaceSecondsPerKm?.let { formatPace(it) } ?: "--:--"
    
    /**
     * 経過時間をフォーマット（HH:MM:SS）
     */
    val formattedDuration: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    
    private fun formatPace(secondsPerKm: Int): String {
        val minutes = secondsPerKm / 60
        val seconds = secondsPerKm % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}

/**
 * ラン画面のViewModel
 */
@HiltViewModel
class RunViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val runRepository: RunRepository
) : ViewModel() {
    
    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""
    
    private val _uiState = MutableStateFlow(RunUiState())
    val uiState: StateFlow<RunUiState> = _uiState.asStateFlow()
    
    // サービスとの接続
    private var locationService: LocationForegroundService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationForegroundService.LocalBinder
            locationService = binder.getService()
            serviceBound = true
            _uiState.value = _uiState.value.copy(isServiceBound = true)
            
            // サービスの状態を監視
            observeServiceState()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            serviceBound = false
            _uiState.value = _uiState.value.copy(isServiceBound = false)
        }
    }
    
    init {
        loadOrStartRun()
        observeCheerMessages()
        observePendingCount()
    }
    
    /**
     * 既存のランを読み込むか、新規開始
     */
    private fun loadOrStartRun() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // 既存の走行中ランがあるか確認
            runRepository.getCurrentRun(eventId)
                .onSuccess { existingRun ->
                    if (existingRun != null) {
                        // 既存のランを再開
                        _uiState.value = _uiState.value.copy(
                            runSession = existingRun,
                            totalDistanceMeters = existingRun.totalDistanceMeters,
                            isLoading = false
                        )
                        observeRunSession(existingRun.id)
                        observePhotos(existingRun.id)
                        bindAndStartService(existingRun.id)
                    } else {
                        // 新規ラン開始
                        startNewRun()
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "ランの読み込みに失敗しました"
                    )
                }
        }
    }
    
    /**
     * 新規ランを開始
     */
    private suspend fun startNewRun() {
        runRepository.startRun(eventId)
            .onSuccess { runSession ->
                _uiState.value = _uiState.value.copy(
                    runSession = runSession,
                    isLoading = false
                )
                observeRunSession(runSession.id)
                observePhotos(runSession.id)
                bindAndStartService(runSession.id)
            }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "ランの開始に失敗しました"
                )
            }
    }
    
    /**
     * サービスをバインドして開始
     */
    private fun bindAndStartService(runId: String) {
        val context = application.applicationContext
        
        // サービス開始
        val startIntent = LocationForegroundService.createStartIntent(context, runId, eventId)
        context.startForegroundService(startIntent)
        
        // バインド
        context.bindService(
            startIntent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }
    
    /**
     * サービスの状態を監視
     */
    private fun observeServiceState() {
        val service = locationService ?: return
        
        viewModelScope.launch {
            service.isTracking.collect { isTracking ->
                _uiState.value = _uiState.value.copy(isTracking = isTracking)
            }
        }
        
        viewModelScope.launch {
            service.lastLocation.collect { location ->
                location?.let {
                    val paceSecondsPerKm = calculatePace(it.speed)
                    _uiState.value = _uiState.value.copy(
                        lastLocation = it,
                        currentPaceSecondsPerKm = paceSecondsPerKm
                    )
                }
            }
        }
        
        viewModelScope.launch {
            service.totalDistance.collect { distance ->
                _uiState.value = _uiState.value.copy(totalDistanceMeters = distance)
                updateAveragePace(distance)
            }
        }
    }
    
    /**
     * ランセッションを監視
     */
    private fun observeRunSession(runId: String) {
        viewModelScope.launch {
            runRepository.observeRun(runId)
                .catch { /* エラーは無視 */ }
                .collect { session ->
                    session?.let {
                        _uiState.value = _uiState.value.copy(
                            runSession = it,
                            totalDistanceMeters = it.totalDistanceMeters
                        )
                        
                        // 終了していたら画面遷移
                        if (it.status == RunStatus.FINISHED) {
                            _uiState.value = _uiState.value.copy(isFinished = true)
                        }
                    }
                }
        }
    }
    
    /**
     * 応援メッセージを監視
     */
    private fun observeCheerMessages() {
        viewModelScope.launch {
            runRepository.observeCheerMessages(eventId)
                .catch { /* エラーは無視 */ }
                .collect { messages ->
                    _uiState.value = _uiState.value.copy(cheerMessages = messages)
                }
        }
    }
    
    /**
     * 写真を監視
     */
    private fun observePhotos(runId: String) {
        viewModelScope.launch {
            runRepository.observePhotos(runId)
                .catch { /* エラーは無視 */ }
                .collect { photos ->
                    _uiState.value = _uiState.value.copy(photos = photos)
                }
        }
    }
    
    /**
     * 送信待ち件数を監視
     */
    private fun observePendingCount() {
        viewModelScope.launch {
            runRepository.observePendingCount()
                .catch { /* エラーは無視 */ }
                .collect { count ->
                    _uiState.value = _uiState.value.copy(pendingLocationCount = count)
                }
        }
    }
    
    /**
     * 経過時間を更新（UIから定期的に呼ばれる）
     */
    fun updateDuration() {
        val startTime = _uiState.value.runSession?.startedAt?.toDate()?.time ?: return
        val now = System.currentTimeMillis()
        val durationSeconds = (now - startTime) / 1000
        _uiState.value = _uiState.value.copy(durationSeconds = durationSeconds)
    }
    
    /**
     * 速度からペースを計算（秒/km）
     */
    private fun calculatePace(speedMps: Float): Int? {
        if (speedMps <= 0) return null
        // 速度(m/s) → ペース(秒/km)
        val secondsPerKm = (1000 / speedMps).toInt()
        // 現実的な範囲（2分/km〜30分/km）に制限
        return if (secondsPerKm in 120..1800) secondsPerKm else null
    }
    
    /**
     * 平均ペースを更新
     */
    private fun updateAveragePace(distanceMeters: Double) {
        val startTime = _uiState.value.runSession?.startedAt?.toDate()?.time ?: return
        if (distanceMeters <= 0) return
        
        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
        if (elapsedSeconds <= 0) return
        
        // 平均速度(m/s)
        val avgSpeedMps = distanceMeters / elapsedSeconds
        if (avgSpeedMps <= 0) return
        
        // 平均ペース(秒/km)
        val avgPaceSecondsPerKm = (1000 / avgSpeedMps).toInt()
        
        // 現実的な範囲に制限
        if (avgPaceSecondsPerKm in 120..1800) {
            _uiState.value = _uiState.value.copy(averagePaceSecondsPerKm = avgPaceSecondsPerKm)
        }
    }
    
    /**
     * ランを終了（確認テキストが正しい場合のみ）
     */
    fun finishRun(confirmText: String): Boolean {
        if (confirmText != "終了") {
            _uiState.value = _uiState.value.copy(
                errorMessage = "「終了」と入力してください"
            )
            return false
        }
        
        val runId = _uiState.value.runSession?.id ?: return false
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFinishing = true)
            
            // サービス停止
            stopService()
            
            // Firestoreを更新
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
                        errorMessage = e.message ?: "終了処理に失敗しました"
                    )
                }
        }
        
        return true
    }
    
    /**
     * サービスを停止
     */
    private fun stopService() {
        val context = application.applicationContext
        
        // アンバインド
        if (serviceBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                // 無視
            }
            serviceBound = false
        }
        
        // サービス停止
        val stopIntent = LocationForegroundService.createStopIntent(context)
        context.startService(stopIntent)
    }
    
    /**
     * 現在地を取得
     */
    fun getCurrentLocation(): Location? = _uiState.value.lastLocation
    
    /**
     * エラーをクリア
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // サービスのアンバインド（終了はしない）
        if (serviceBound) {
            try {
                application.applicationContext.unbindService(serviceConnection)
            } catch (e: Exception) {
                // 無視
            }
        }
    }
}

