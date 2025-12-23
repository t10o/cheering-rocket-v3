package one.t10o.cheering_rocket.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import one.t10o.cheering_rocket.data.local.entity.PendingLocationEntity

/**
 * 送信待ち位置情報のDAO
 */
@Dao
interface PendingLocationDao {
    
    /**
     * 送信待ち位置情報を追加
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: PendingLocationEntity): Long
    
    /**
     * 複数の送信待ち位置情報を追加
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<PendingLocationEntity>): List<Long>
    
    /**
     * 送信待ち位置情報を更新
     */
    @Update
    suspend fun update(location: PendingLocationEntity)
    
    /**
     * 送信待ち位置情報を削除
     */
    @Delete
    suspend fun delete(location: PendingLocationEntity)
    
    /**
     * IDで削除
     */
    @Query("DELETE FROM pending_locations WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /**
     * 複数のIDで削除
     */
    @Query("DELETE FROM pending_locations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
    
    /**
     * 特定のランの送信待ち位置情報を全て削除
     */
    @Query("DELETE FROM pending_locations WHERE runId = :runId")
    suspend fun deleteByRunId(runId: String)
    
    /**
     * 全ての送信待ち位置情報を取得（古い順）
     */
    @Query("SELECT * FROM pending_locations ORDER BY timestamp ASC")
    suspend fun getAll(): List<PendingLocationEntity>
    
    /**
     * 全ての送信待ち位置情報を監視（古い順）
     */
    @Query("SELECT * FROM pending_locations ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<PendingLocationEntity>>
    
    /**
     * 特定のランの送信待ち位置情報を取得（古い順）
     */
    @Query("SELECT * FROM pending_locations WHERE runId = :runId ORDER BY timestamp ASC")
    suspend fun getByRunId(runId: String): List<PendingLocationEntity>
    
    /**
     * 特定のランの送信待ち位置情報を監視
     */
    @Query("SELECT * FROM pending_locations WHERE runId = :runId ORDER BY timestamp ASC")
    fun observeByRunId(runId: String): Flow<List<PendingLocationEntity>>
    
    /**
     * 送信待ち件数を取得
     */
    @Query("SELECT COUNT(*) FROM pending_locations")
    suspend fun getPendingCount(): Int
    
    /**
     * 送信待ち件数を監視
     */
    @Query("SELECT COUNT(*) FROM pending_locations")
    fun observePendingCount(): Flow<Int>
    
    /**
     * リトライ上限を超えたものを取得
     */
    @Query("SELECT * FROM pending_locations WHERE retryCount >= :maxRetry")
    suspend fun getFailedLocations(maxRetry: Int): List<PendingLocationEntity>
    
    /**
     * リトライ回数をインクリメント
     */
    @Query("UPDATE pending_locations SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)
    
    /**
     * 古いデータを削除（保持期間を超えたもの）
     */
    @Query("DELETE FROM pending_locations WHERE createdAt < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}

