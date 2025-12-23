package one.t10o.cheering_rocket.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import one.t10o.cheering_rocket.data.local.dao.PendingLocationDao
import one.t10o.cheering_rocket.data.local.entity.PendingLocationEntity

/**
 * アプリのローカルデータベース
 * 送信待ちキューや直近ログを保持
 */
@Database(
    entities = [PendingLocationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun pendingLocationDao(): PendingLocationDao
    
    companion object {
        const val DATABASE_NAME = "cheering_rocket_db"
    }
}

