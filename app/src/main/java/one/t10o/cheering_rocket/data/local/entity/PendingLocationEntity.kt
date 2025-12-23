package one.t10o.cheering_rocket.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 送信待ち位置情報
 * ネットワーク断時のローカルキュー用
 */
@Entity(tableName = "pending_locations")
data class PendingLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val runId: String,
    val eventId: String,
    val userId: String,
    
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Float?,
    val speedMps: Double?,
    val bearing: Float?,
    val timestamp: Long,  // epoch millis
    
    val distanceFromPrevious: Double?,
    val cumulativeDistance: Double,
    
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

