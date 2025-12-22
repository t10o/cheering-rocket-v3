package one.t10o.cheering_rocket.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud Storage関連のリポジトリ
 * 画像のアップロード・削除を管理
 */
@Singleton
class StorageRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseStorage: FirebaseStorage
) {
    /**
     * プロフィール画像をアップロード
     * @param imageUri ローカルの画像URI
     * @return アップロードされた画像のダウンロードURL
     */
    suspend fun uploadProfileImage(imageUri: Uri): Result<String> {
        return try {
            val uid = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ユーザーが見つかりません"))
            
            val fileName = "profile_${UUID.randomUUID()}.jpg"
            val storageRef = firebaseStorage.reference
                .child("users")
                .child(uid)
                .child(fileName)
            
            // アップロード
            storageRef.putFile(imageUri).await()
            
            // ダウンロードURLを取得
            val downloadUrl = storageRef.downloadUrl.await().toString()
            
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * イベント写真をアップロード
     * @param eventId イベントID
     * @param imageUri ローカルの画像URI
     * @return アップロードされた画像のダウンロードURL
     */
    suspend fun uploadEventPhoto(eventId: String, imageUri: Uri): Result<String> {
        return try {
            val uid = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ユーザーが見つかりません"))
            
            val fileName = "photo_${UUID.randomUUID()}.jpg"
            val storageRef = firebaseStorage.reference
                .child("events")
                .child(eventId)
                .child(uid)
                .child(fileName)
            
            // アップロード
            storageRef.putFile(imageUri).await()
            
            // ダウンロードURLを取得
            val downloadUrl = storageRef.downloadUrl.await().toString()
            
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

