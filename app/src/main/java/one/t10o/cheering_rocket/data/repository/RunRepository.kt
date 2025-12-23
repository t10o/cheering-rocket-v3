package one.t10o.cheering_rocket.data.repository

import android.location.Location
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import one.t10o.cheering_rocket.data.local.dao.PendingLocationDao
import one.t10o.cheering_rocket.data.local.entity.PendingLocationEntity
import one.t10o.cheering_rocket.data.model.CheerMessage
import one.t10o.cheering_rocket.data.model.LatestLocation
import one.t10o.cheering_rocket.data.model.RunLocation
import one.t10o.cheering_rocket.data.model.RunPhoto
import one.t10o.cheering_rocket.data.model.RunSession
import one.t10o.cheering_rocket.data.model.RunStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ラン機能のリポジトリ
 * 位置情報の保存、応援メッセージの取得、写真管理を担当
 */
@Singleton
class RunRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val pendingLocationDao: PendingLocationDao
) {
    
    companion object {
        private const val MAX_RETRY_COUNT = 5
        private const val EARTH_RADIUS_METERS = 6371000.0
    }
    
    // ==================== ランセッション管理 ====================
    
    /**
     * ランを開始（新しいRunSessionを作成）
     */
    suspend fun startRun(eventId: String): Result<RunSession> {
        return try {
            val currentUser = firebaseAuth.currentUser
                ?: return Result.failure(Exception("ログインしていません"))
            
            // ユーザー情報を取得
            val userDoc = firestore.collection("users")
                .document(currentUser.uid)
                .get()
                .await()
            
            val userName = userDoc.getString("displayName") ?: "名前なし"
            val userPhotoUrl = userDoc.getString("photoUrl")
            
            val now = Timestamp.now()
            
            val runDoc = hashMapOf(
                "eventId" to eventId,
                "userId" to currentUser.uid,
                "userName" to userName,
                "userPhotoUrl" to userPhotoUrl,
                "status" to RunStatus.RUNNING.name,
                "startedAt" to now,
                "finishedAt" to null,
                "latestLocation" to null,
                "totalDistanceMeters" to 0.0,
                "createdAt" to now,
                "updatedAt" to now
            )
            
            val docRef = firestore.collection("runs")
                .add(runDoc)
                .await()
            
            // イベントのステータスをRUNNINGに更新
            firestore.collection("events")
                .document(eventId)
                .update("status", "RUNNING", "updatedAt", now)
                .await()
            
            val runSession = RunSession(
                id = docRef.id,
                eventId = eventId,
                userId = currentUser.uid,
                userName = userName,
                userPhotoUrl = userPhotoUrl,
                status = RunStatus.RUNNING,
                startedAt = now,
                createdAt = now,
                updatedAt = now
            )
            
            Result.success(runSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * ランを終了
     */
    suspend fun finishRun(runId: String): Result<Unit> {
        return try {
            val now = Timestamp.now()
            
            firestore.collection("runs")
                .document(runId)
                .update(
                    mapOf(
                        "status" to RunStatus.FINISHED.name,
                        "finishedAt" to now,
                        "updatedAt" to now
                    )
                )
                .await()
            
            // ローカルの送信待ちデータを全て送信試行
            syncPendingLocations(runId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 現在のランセッションを取得
     */
    suspend fun getCurrentRun(eventId: String): Result<RunSession?> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ログインしていません"))
            
            val snapshot = firestore.collection("runs")
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("status", RunStatus.RUNNING.name)
                .limit(1)
                .get()
                .await()
            
            if (snapshot.isEmpty) {
                return Result.success(null)
            }
            
            val doc = snapshot.documents.first()
            val runSession = parseRunSession(doc)
            
            Result.success(runSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * ランセッションを監視
     */
    fun observeRun(runId: String): Flow<RunSession?> = callbackFlow {
        val listener = firestore.collection("runs")
            .document(runId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                
                val runSession = parseRunSession(snapshot)
                trySend(runSession)
            }
        
        awaitClose { listener.remove() }
    }
    
    // ==================== 位置情報管理 ====================
    
    /**
     * 位置情報を保存（ローカル→リモートの二重書き込み）
     */
    suspend fun saveLocation(
        runId: String,
        eventId: String,
        location: Location,
        previousLocation: RunLocation?
    ): Result<RunLocation> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ログインしていません"))
            
            val now = Timestamp.now()
            val timestamp = location.time
            
            // 前回地点からの距離を計算
            val distanceFromPrevious = previousLocation?.let {
                calculateDistance(
                    it.latitude, it.longitude,
                    location.latitude, location.longitude
                )
            }
            
            // 累積距離
            val cumulativeDistance = (previousLocation?.cumulativeDistance ?: 0.0) + 
                (distanceFromPrevious ?: 0.0)
            
            // まずローカルに保存（送信待ちキュー）
            val pendingEntity = PendingLocationEntity(
                runId = runId,
                eventId = eventId,
                userId = currentUserId,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                speedMps = if (location.hasSpeed()) location.speed.toDouble() else null,
                bearing = if (location.hasBearing()) location.bearing else null,
                timestamp = timestamp,
                distanceFromPrevious = distanceFromPrevious,
                cumulativeDistance = cumulativeDistance
            )
            
            val localId = pendingLocationDao.insert(pendingEntity)
            
            // Firestoreへの保存を試行
            try {
                saveLocationToFirestore(
                    runId = runId,
                    location = location,
                    distanceFromPrevious = distanceFromPrevious,
                    cumulativeDistance = cumulativeDistance
                )
                
                // 成功したらローカルから削除
                pendingLocationDao.deleteById(localId)
            } catch (e: Exception) {
                // Firestore保存失敗はローカルに残して後で再送
                // エラーは握りつぶさず、ログに残す（本番ではCrashlyticsなど）
                android.util.Log.w("RunRepository", "Failed to sync location to Firestore", e)
            }
            
            val runLocation = RunLocation(
                id = localId.toString(),
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                speedMps = if (location.hasSpeed()) location.speed.toDouble() else null,
                bearing = if (location.hasBearing()) location.bearing else null,
                timestamp = now,
                distanceFromPrevious = distanceFromPrevious,
                cumulativeDistance = cumulativeDistance,
                isSynced = pendingLocationDao.getByRunId(runId).none { it.id == localId }
            )
            
            Result.success(runLocation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Firestoreに位置情報を保存
     */
    private suspend fun saveLocationToFirestore(
        runId: String,
        location: Location,
        distanceFromPrevious: Double?,
        cumulativeDistance: Double
    ) {
        val now = Timestamp.now()
        
        val locationDoc = hashMapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "altitude" to if (location.hasAltitude()) location.altitude else null,
            "accuracy" to if (location.hasAccuracy()) location.accuracy else null,
            "speedMps" to if (location.hasSpeed()) location.speed.toDouble() else null,
            "bearing" to if (location.hasBearing()) location.bearing else null,
            "timestamp" to Timestamp(location.time / 1000, ((location.time % 1000) * 1000000).toInt()),
            "distanceFromPrevious" to distanceFromPrevious,
            "cumulativeDistance" to cumulativeDistance
        )
        
        // サブコレクションに追加
        firestore.collection("runs")
            .document(runId)
            .collection("locations")
            .add(locationDoc)
            .await()
        
        // 最新位置情報とトータル距離をrunドキュメントに更新
        val latestLocation = hashMapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "timestamp" to now,
            "speedMps" to if (location.hasSpeed()) location.speed.toDouble() else null
        )
        
        firestore.collection("runs")
            .document(runId)
            .update(
                mapOf(
                    "latestLocation" to latestLocation,
                    "totalDistanceMeters" to cumulativeDistance,
                    "updatedAt" to now
                )
            )
            .await()
    }
    
    /**
     * 送信待ちの位置情報を同期
     */
    suspend fun syncPendingLocations(runId: String? = null): Result<Int> {
        return try {
            val pendingLocations = if (runId != null) {
                pendingLocationDao.getByRunId(runId)
            } else {
                pendingLocationDao.getAll()
            }
            
            var syncedCount = 0
            val toDelete = mutableListOf<Long>()
            
            for (pending in pendingLocations) {
                try {
                    val locationDoc = hashMapOf(
                        "latitude" to pending.latitude,
                        "longitude" to pending.longitude,
                        "altitude" to pending.altitude,
                        "accuracy" to pending.accuracy,
                        "speedMps" to pending.speedMps,
                        "bearing" to pending.bearing,
                        "timestamp" to Timestamp(pending.timestamp / 1000, ((pending.timestamp % 1000) * 1000000).toInt()),
                        "distanceFromPrevious" to pending.distanceFromPrevious,
                        "cumulativeDistance" to pending.cumulativeDistance
                    )
                    
                    firestore.collection("runs")
                        .document(pending.runId)
                        .collection("locations")
                        .add(locationDoc)
                        .await()
                    
                    toDelete.add(pending.id)
                    syncedCount++
                } catch (e: Exception) {
                    // リトライ回数をインクリメント
                    pendingLocationDao.incrementRetryCount(pending.id)
                    
                    if (pending.retryCount >= MAX_RETRY_COUNT) {
                        // 最大リトライ回数を超えたら削除（データロスを許容）
                        toDelete.add(pending.id)
                    }
                }
            }
            
            if (toDelete.isNotEmpty()) {
                pendingLocationDao.deleteByIds(toDelete)
            }
            
            Result.success(syncedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 位置情報履歴を取得
     */
    fun observeLocationHistory(runId: String): Flow<List<RunLocation>> = callbackFlow {
        val listener = firestore.collection("runs")
            .document(runId)
            .collection("locations")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val locations = snapshot?.documents?.mapNotNull { doc ->
                    RunLocation(
                        id = doc.id,
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        altitude = doc.getDouble("altitude"),
                        accuracy = doc.getDouble("accuracy")?.toFloat(),
                        speedMps = doc.getDouble("speedMps"),
                        bearing = doc.getDouble("bearing")?.toFloat(),
                        timestamp = doc.getTimestamp("timestamp"),
                        distanceFromPrevious = doc.getDouble("distanceFromPrevious"),
                        cumulativeDistance = doc.getDouble("cumulativeDistance") ?: 0.0,
                        isSynced = true
                    )
                } ?: emptyList()
                
                trySend(locations)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * 送信待ち件数を監視
     */
    fun observePendingCount(): Flow<Int> = pendingLocationDao.observePendingCount()
    
    // ==================== 応援メッセージ ====================
    
    /**
     * 応援メッセージを監視
     */
    fun observeCheerMessages(eventId: String): Flow<List<CheerMessage>> = callbackFlow {
        val listener = firestore.collection("events")
            .document(eventId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)  // 直近50件
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    CheerMessage(
                        id = doc.id,
                        eventId = eventId,
                        senderName = doc.getString("senderName") ?: "匿名",
                        senderPhotoUrl = doc.getString("senderPhotoUrl"),
                        text = doc.getString("text") ?: "",
                        createdAt = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()
                
                trySend(messages)
            }
        
        awaitClose { listener.remove() }
    }
    
    // ==================== 写真管理 ====================
    
    /**
     * 写真を保存
     */
    suspend fun savePhoto(
        runId: String,
        storagePath: String,
        downloadUrl: String,
        latitude: Double,
        longitude: Double
    ): Result<RunPhoto> {
        return try {
            val now = Timestamp.now()
            
            val photoDoc = hashMapOf(
                "storagePath" to storagePath,
                "downloadUrl" to downloadUrl,
                "latitude" to latitude,
                "longitude" to longitude,
                "timestamp" to now
            )
            
            val docRef = firestore.collection("runs")
                .document(runId)
                .collection("photos")
                .add(photoDoc)
                .await()
            
            val photo = RunPhoto(
                id = docRef.id,
                runId = runId,
                storagePath = storagePath,
                downloadUrl = downloadUrl,
                latitude = latitude,
                longitude = longitude,
                timestamp = now
            )
            
            Result.success(photo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 写真を監視
     */
    fun observePhotos(runId: String): Flow<List<RunPhoto>> = callbackFlow {
        val listener = firestore.collection("runs")
            .document(runId)
            .collection("photos")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val photos = snapshot?.documents?.mapNotNull { doc ->
                    RunPhoto(
                        id = doc.id,
                        runId = runId,
                        storagePath = doc.getString("storagePath") ?: "",
                        downloadUrl = doc.getString("downloadUrl") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        timestamp = doc.getTimestamp("timestamp"),
                        caption = doc.getString("caption")
                    )
                } ?: emptyList()
                
                trySend(photos)
            }
        
        awaitClose { listener.remove() }
    }
    
    // ==================== ユーティリティ ====================
    
    /**
     * 2点間の距離を計算（Haversine公式）
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return EARTH_RADIUS_METERS * c
    }
    
    /**
     * RunSessionをパース
     */
    private fun parseRunSession(doc: com.google.firebase.firestore.DocumentSnapshot): RunSession {
        val latestLocationMap = doc.get("latestLocation") as? Map<*, *>
        val latestLocation = latestLocationMap?.let {
            LatestLocation(
                latitude = (it["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (it["longitude"] as? Number)?.toDouble() ?: 0.0,
                timestamp = it["timestamp"] as? Timestamp,
                speedMps = (it["speedMps"] as? Number)?.toDouble()
            )
        }
        
        return RunSession(
            id = doc.id,
            eventId = doc.getString("eventId") ?: "",
            userId = doc.getString("userId") ?: "",
            userName = doc.getString("userName") ?: "",
            userPhotoUrl = doc.getString("userPhotoUrl"),
            status = try {
                RunStatus.valueOf(doc.getString("status") ?: RunStatus.RUNNING.name)
            } catch (e: Exception) {
                RunStatus.RUNNING
            },
            startedAt = doc.getTimestamp("startedAt"),
            finishedAt = doc.getTimestamp("finishedAt"),
            latestLocation = latestLocation,
            totalDistanceMeters = doc.getDouble("totalDistanceMeters") ?: 0.0,
            createdAt = doc.getTimestamp("createdAt"),
            updatedAt = doc.getTimestamp("updatedAt")
        )
    }
    
    /**
     * 自分の走行中ランを全て取得
     */
    suspend fun getMyActiveRuns(): Result<List<RunSession>> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ログインしていません"))
            
            val snapshot = firestore.collection("runs")
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("status", RunStatus.RUNNING.name)
                .get()
                .await()
            
            val runs = snapshot.documents.mapNotNull { doc ->
                parseRunSession(doc)
            }
            
            Result.success(runs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 自分の走行中ランをリアルタイム監視
     */
    fun observeMyActiveRuns(): Flow<List<RunSession>> = callbackFlow {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("runs")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("status", RunStatus.RUNNING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val runs = snapshot?.documents?.mapNotNull { doc ->
                    parseRunSession(doc)
                } ?: emptyList()
                
                trySend(runs)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * 現在のユーザーIDを取得
     */
    fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
}

