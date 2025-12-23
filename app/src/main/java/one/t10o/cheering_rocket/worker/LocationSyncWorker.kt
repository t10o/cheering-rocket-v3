package one.t10o.cheering_rocket.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import one.t10o.cheering_rocket.data.repository.RunRepository
import java.util.concurrent.TimeUnit

/**
 * 位置情報同期Worker
 * 
 * ネットワーク復旧時に送信待ちの位置情報をFirestoreに同期する。
 * WorkManagerのリトライ機能を活用し、確実なアップロードを保証する。
 * 
 * 参考: https://developer.android.com/develop/background-work
 */
@HiltWorker
class LocationSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val runRepository: RunRepository
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "LocationSyncWorker"
        
        // ワーク名
        const val PERIODIC_WORK_NAME = "location_sync_periodic"
        const val ONE_TIME_WORK_NAME = "location_sync_one_time"
        
        // 定期実行間隔（分）
        private const val PERIODIC_INTERVAL_MINUTES = 15L
        
        /**
         * 定期同期をスケジュール
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val request = PeriodicWorkRequestBuilder<LocationSyncWorker>(
                PERIODIC_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.MINUTES
                )
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            
            Log.d(TAG, "Scheduled periodic location sync")
        }
        
        /**
         * 即座に同期を実行
         */
        fun syncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val request = OneTimeWorkRequestBuilder<LocationSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()
            
            WorkManager.getInstance(context)
                .enqueue(request)
            
            Log.d(TAG, "Enqueued one-time location sync")
        }
        
        /**
         * 定期同期をキャンセル
         */
        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(PERIODIC_WORK_NAME)
            
            Log.d(TAG, "Cancelled periodic location sync")
        }
    }
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting location sync...")
        
        return try {
            val result = runRepository.syncPendingLocations()
            
            result.fold(
                onSuccess = { syncedCount ->
                    Log.d(TAG, "Synced $syncedCount locations")
                    Result.success()
                },
                onFailure = { e ->
                    Log.e(TAG, "Sync failed", e)
                    // リトライ
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sync", e)
            Result.retry()
        }
    }
}

