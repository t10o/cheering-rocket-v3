package one.t10o.cheering_rocket

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * アプリケーションクラス
 * Hilt の依存性注入を有効化
 * WorkManager の Hilt 連携を設定
 */
@HiltAndroidApp
class CheeringRocketApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

