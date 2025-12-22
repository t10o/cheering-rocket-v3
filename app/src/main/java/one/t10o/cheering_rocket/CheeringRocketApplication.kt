package one.t10o.cheering_rocket

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * アプリケーションクラス
 * Hilt の依存性注入を有効化
 */
@HiltAndroidApp
class CheeringRocketApplication : Application()

