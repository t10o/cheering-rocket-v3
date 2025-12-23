package one.t10o.cheering_rocket.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.MainActivity
import one.t10o.cheering_rocket.R
import one.t10o.cheering_rocket.data.model.RunLocation
import one.t10o.cheering_rocket.data.repository.RunRepository
import javax.inject.Inject

/**
 * 位置情報取得用 Foreground Service
 * 
 * バックグラウンドでも位置情報を継続的に取得するためのサービス。
 * Android の Foreground Service (type=location) として動作し、
 * ユーザーに通知を表示しながら位置情報を収集する。
 * 
 * 参考: https://developer.android.com/develop/background-work/services/fgs/service-types
 */
@AndroidEntryPoint
class LocationForegroundService : Service() {
    
    companion object {
        private const val TAG = "LocationForegroundSvc"
        
        // Notification
        const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
        const val NOTIFICATION_ID = 1001
        
        // Intent Actions
        const val ACTION_START = "one.t10o.cheering_rocket.action.START_LOCATION_TRACKING"
        const val ACTION_STOP = "one.t10o.cheering_rocket.action.STOP_LOCATION_TRACKING"
        
        // Intent Extras
        const val EXTRA_RUN_ID = "run_id"
        const val EXTRA_EVENT_ID = "event_id"
        
        // Location Settings
        // 1分間隔を基準（安定優先）
        private const val LOCATION_INTERVAL_MS = 60_000L  // 60秒
        private const val LOCATION_FASTEST_INTERVAL_MS = 30_000L  // 最速30秒
        private const val LOCATION_MIN_DISTANCE_METERS = 10f  // 最小移動距離10m
        
        /**
         * サービス開始用のIntent作成
         */
        fun createStartIntent(context: Context, runId: String, eventId: String): Intent {
            return Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RUN_ID, runId)
                putExtra(EXTRA_EVENT_ID, eventId)
            }
        }
        
        /**
         * サービス停止用のIntent作成
         */
        fun createStopIntent(context: Context): Intent {
            return Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
    
    @Inject
    lateinit var runRepository: RunRepository
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    
    private var runId: String? = null
    private var eventId: String? = null
    
    // 前回の位置情報（距離計算用）
    private var previousLocation: RunLocation? = null
    
    // 状態管理
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    
    private val _lastLocation = MutableStateFlow<Location?>(null)
    val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()
    
    private val _totalDistance = MutableStateFlow(0.0)
    val totalDistance: StateFlow<Double> = _totalDistance.asStateFlow()
    
    private val _locationCount = MutableStateFlow(0)
    val locationCount: StateFlow<Int> = _locationCount.asStateFlow()
    
    // Binder for activity binding
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): LocationForegroundService = this@LocationForegroundService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                runId = intent.getStringExtra(EXTRA_RUN_ID)
                eventId = intent.getStringExtra(EXTRA_EVENT_ID)
                
                if (runId != null && eventId != null) {
                    startForegroundService()
                    startLocationUpdates()
                } else {
                    Log.e(TAG, "Missing runId or eventId")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        
        // サービスが kill された場合に再起動しない
        // （ユーザーが明示的に終了した場合を想定）
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        stopLocationUpdates()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    /**
     * Foreground Service として開始
     */
    private fun startForegroundService() {
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        Log.d(TAG, "Started foreground service")
    }
    
    /**
     * 位置情報の更新を開始
     */
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            stopSelf()
            return
        }
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MS)
            .setMinUpdateDistanceMeters(LOCATION_MIN_DISTANCE_METERS)
            .build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
        
        _isTracking.value = true
        Log.d(TAG, "Started location updates")
    }
    
    /**
     * 位置情報の更新を停止
     */
    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
        
        _isTracking.value = false
        Log.d(TAG, "Stopped location updates")
    }
    
    /**
     * 位置情報の更新を処理
     */
    private fun handleLocationUpdate(location: Location) {
        Log.d(TAG, "Location update: lat=${location.latitude}, lon=${location.longitude}")
        
        _lastLocation.value = location
        
        val currentRunId = runId ?: return
        val currentEventId = eventId ?: return
        
        serviceScope.launch {
            runRepository.saveLocation(
                runId = currentRunId,
                eventId = currentEventId,
                location = location,
                previousLocation = previousLocation
            )
                .onSuccess { runLocation ->
                    previousLocation = runLocation
                    _totalDistance.value = runLocation.cumulativeDistance
                    _locationCount.value = _locationCount.value + 1
                    
                    // 通知を更新
                    updateNotification(runLocation.cumulativeDistance)
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to save location", e)
                }
        }
    }
    
    /**
     * 通知チャンネルを作成
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "位置情報トラッキング",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "ラン中の位置情報を記録しています"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * 通知を作成
     */
    private fun createNotification(distanceMeters: Double = 0.0): Notification {
        // アプリを開くIntent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 停止用Intent
        val stopIntent = PendingIntent.getService(
            this,
            1,
            createStopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val distanceText = formatDistance(distanceMeters)
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🏃 ラン中")
            .setContentText("距離: $distanceText")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "停止",
                stopIntent
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * 通知を更新
     */
    private fun updateNotification(distanceMeters: Double) {
        val notification = createNotification(distanceMeters)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * 距離をフォーマット
     */
    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format("%.2f km", meters / 1000)
        } else {
            String.format("%.0f m", meters)
        }
    }
}

