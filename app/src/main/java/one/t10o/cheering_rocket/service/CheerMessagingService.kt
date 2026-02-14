package one.t10o.cheering_rocket.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import one.t10o.cheering_rocket.MainActivity
import one.t10o.cheering_rocket.R
import javax.inject.Inject

/**
 * 応援メッセージのプッシュ通知を受信するサービス
 * 
 * Cloud Functions からの FCM 通知を受信し、ランナーに表示する。
 * 
 * 参考: https://firebase.google.com/docs/cloud-messaging
 */
@AndroidEntryPoint
class CheerMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "CheerMessagingService"
        
        // 通知チャンネル
        const val CHEER_MESSAGE_CHANNEL_ID = "cheer_message_channel"
        private const val CHEER_MESSAGE_NOTIFICATION_ID = 2001
    }
    
    @Inject
    lateinit var firebaseAuth: FirebaseAuth
    
    @Inject
    lateinit var firestore: FirebaseFirestore
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    /**
     * 新しいトークンが発行された時の処理
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        
        // トークンをFirestoreに保存
        saveTokenToFirestore(token)
    }
    
    /**
     * メッセージ受信時の処理
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d(TAG, "Message received: ${message.data}")
        
        // データペイロードから情報を取得
        val data = message.data
        val messageType = data["type"]
        
        when (messageType) {
            "cheer_message" -> {
                handleCheerMessage(
                    senderName = data["senderName"] ?: "匿名",
                    messageText = data["text"] ?: "",
                    eventId = data["eventId"]
                )
            }
            else -> {
                // 通知ペイロードがある場合はそのまま表示
                message.notification?.let { notification ->
                    showNotification(
                        title = notification.title ?: "Cheering Rocket",
                        body = notification.body ?: ""
                    )
                }
            }
        }
    }
    
    /**
     * 応援メッセージの通知を表示
     */
    private fun handleCheerMessage(
        senderName: String,
        messageText: String,
        eventId: String?
    ) {
        showNotification(
            title = "📣 $senderName さんからの応援",
            body = messageText,
            eventId = eventId
        )
    }
    
    /**
     * 通知を表示
     */
    private fun showNotification(
        title: String,
        body: String,
        eventId: String? = null
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            eventId?.let { putExtra("eventId", it) }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHEER_MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        // 通知IDは時刻ベースでユニークにする
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(notificationId, notification)
    }
    
    /**
     * 通知チャンネルを作成
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHEER_MESSAGE_CHANNEL_ID,
            "応援メッセージ",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "応援者からのメッセージ通知"
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * FCMトークンをFirestoreに保存
     */
    private fun saveTokenToFirestore(token: String) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        serviceScope.launch {
            val now = Timestamp.now()
            try {
                firestore.collection("users")
                    .document(currentUserId)
                    .update(
                        mapOf(
                            "fcmToken" to token,
                            "updatedAt" to now
                        )
                    )
                    .await()

                syncActiveRunTokens(currentUserId, token, now)
                Log.d(TAG, "FCM token saved to Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save FCM token", e)
            }
        }
    }

    /**
     * 走行中ランに最新FCMトークンを反映
     */
    private suspend fun syncActiveRunTokens(userId: String, token: String, now: Timestamp) {
        val activeRuns = firestore.collection("runs")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "RUNNING")
            .get()
            .await()

        if (activeRuns.isEmpty) {
            return
        }

        val batch = firestore.batch()
        activeRuns.documents.forEach { doc ->
            batch.update(
                doc.reference,
                mapOf(
                    "runnerFcmToken" to token,
                    "updatedAt" to now
                )
            )
        }
        batch.commit().await()

        Log.d(TAG, "FCM token synced to ${activeRuns.documents.size} active runs")
    }
}

