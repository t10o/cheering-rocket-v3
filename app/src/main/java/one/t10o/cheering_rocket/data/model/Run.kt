package one.t10o.cheering_rocket.data.model

import com.google.firebase.Timestamp

/**
 * ランセッション
 * イベント内で各ユーザーが行う1回のランを表す
 */
data class RunSession(
    val id: String = "",
    val eventId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val status: RunStatus = RunStatus.RUNNING,
    val startedAt: Timestamp? = null,
    val finishedAt: Timestamp? = null,
    val latestLocation: LatestLocation? = null,
    val totalDistanceMeters: Double = 0.0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/**
 * ランの状態
 */
enum class RunStatus {
    RUNNING,    // 走行中
    PAUSED,     // 一時停止（将来用）
    FINISHED    // 終了
}

/**
 * 最新位置情報（runsドキュメントに埋め込み、高速参照用）
 */
data class LatestLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Timestamp? = null,
    val speedMps: Double? = null  // メートル/秒
)

/**
 * 位置情報ログ（runs/{runId}/locations サブコレクション）
 */
data class RunLocation(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double? = null,
    val accuracy: Float? = null,        // メートル
    val speedMps: Double? = null,       // メートル/秒
    val bearing: Float? = null,         // 方位角（度）
    val timestamp: Timestamp? = null,
    val distanceFromPrevious: Double? = null,  // 前回地点からの距離（メートル）
    val cumulativeDistance: Double = 0.0,      // 累積距離（メートル）
    val isSynced: Boolean = false       // Firestoreと同期済みか
)

/**
 * 応援メッセージ（events/{eventId}/messages サブコレクション）
 */
data class CheerMessage(
    val id: String = "",
    val eventId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String? = null,
    val text: String = "",
    val createdAt: Timestamp? = null
)

/**
 * ラン中の写真（runs/{runId}/photos サブコレクション）
 */
data class RunPhoto(
    val id: String = "",
    val runId: String = "",
    val storagePath: String = "",
    val downloadUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Timestamp? = null,
    val caption: String? = null
)

