package one.t10o.cheering_rocket.data.repository

import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import one.t10o.cheering_rocket.data.model.User
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 認証関連のリポジトリ
 * Firebase Authentication と Firestore を使用してユーザー認証を管理
 */
@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    /**
     * 現在の認証状態を監視
     */
    val authState: Flow<User?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            trySend(firebaseUser?.toUser())
        }
        firebaseAuth.addAuthStateListener(authStateListener)
        awaitClose {
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }

    /**
     * 現在のユーザーを取得
     */
    fun getCurrentUser(): User? {
        return firebaseAuth.currentUser?.toUser()
    }

    /**
     * Google認証でサインイン
     * @param account Google Sign-In のアカウント
     * @return サインイン結果（新規ユーザーかどうかを含む）
     */
    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("サインインに失敗しました"))
            
            val isNewUser = authResult.additionalUserInfo?.isNewUser ?: false
            
            // 新規ユーザーの場合、Firestoreにユーザードキュメントを作成
            if (isNewUser) {
                createUserDocument(firebaseUser)
            }
            
            val user = firebaseUser.toUser(isNewUser = isNewUser)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Firestoreにユーザードキュメントを作成
     */
    private suspend fun createUserDocument(firebaseUser: FirebaseUser) {
        val accountId = generateUniqueAccountId()
        val userDoc = hashMapOf(
            "uid" to firebaseUser.uid,
            "email" to firebaseUser.email,
            "displayName" to firebaseUser.displayName,
            "photoUrl" to firebaseUser.photoUrl?.toString(),
            "accountId" to accountId,
            "createdAt" to com.google.firebase.Timestamp.now(),
            "profileCompleted" to false
        )
        firestore.collection("users")
            .document(firebaseUser.uid)
            .set(userDoc)
            .await()
    }

    /**
     * 共有用のアカウントIDを生成（一意性を保証）
     * Firestoreで既存のIDと重複しないことを確認してから返す
     */
    private suspend fun generateUniqueAccountId(): String {
        var attempts = 0
        val maxAttempts = 10
        
        while (attempts < maxAttempts) {
            val candidateId = generateAccountIdCandidate()
            
            // Firestoreで重複チェック
            val existingUsers = firestore.collection("users")
                .whereEqualTo("accountId", candidateId)
                .limit(1)
                .get()
                .await()
            
            if (existingUsers.isEmpty) {
                return candidateId
            }
            
            attempts++
        }
        
        // 万が一10回試しても重複する場合は、より長いIDを生成
        return UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
    }
    
    /**
     * アカウントID候補を生成
     */
    private fun generateAccountIdCandidate(): String {
        return UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
    }

    /**
     * プロフィール設定完了を更新
     * ドキュメントが存在しない場合は作成する
     */
    suspend fun completeProfileSetup(displayName: String, photoUrl: String?): Result<Unit> {
        return try {
            val firebaseUser = firebaseAuth.currentUser
                ?: return Result.failure(Exception("ユーザーが見つかりません"))
            
            val uid = firebaseUser.uid
            
            // ドキュメントが存在するか確認
            val docRef = firestore.collection("users").document(uid)
            val doc = docRef.get().await()
            
            if (doc.exists()) {
                // 既存ドキュメントを更新
                val updates = hashMapOf<String, Any>(
                    "displayName" to displayName,
                    "profileCompleted" to true
                )
                photoUrl?.let { updates["photoUrl"] = it }
                docRef.update(updates).await()
            } else {
                // ドキュメントが存在しない場合は新規作成
                val accountId = generateUniqueAccountId()
                val userDoc = hashMapOf(
                    "uid" to uid,
                    "email" to firebaseUser.email,
                    "displayName" to displayName,
                    "photoUrl" to (photoUrl ?: firebaseUser.photoUrl?.toString()),
                    "accountId" to accountId,
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "profileCompleted" to true
                )
                docRef.set(userDoc).await()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * プロフィール設定が完了しているか確認
     */
    suspend fun isProfileCompleted(): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false
        return try {
            val doc = firestore.collection("users")
                .document(uid)
                .get()
                .await()
            doc.getBoolean("profileCompleted") ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * ユーザー情報を取得
     */
    suspend fun getUserProfile(): Result<Map<String, Any?>> {
        return try {
            val uid = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ユーザーが見つかりません"))
            
            val doc = firestore.collection("users")
                .document(uid)
                .get()
                .await()
            
            Result.success(doc.data ?: emptyMap())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * サインアウト
     */
    fun signOut() {
        firebaseAuth.signOut()
    }

    /**
     * FirebaseUser を User に変換
     */
    private fun FirebaseUser.toUser(isNewUser: Boolean = false): User {
        return User(
            uid = uid,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl?.toString(),
            isNewUser = isNewUser
        )
    }
}

