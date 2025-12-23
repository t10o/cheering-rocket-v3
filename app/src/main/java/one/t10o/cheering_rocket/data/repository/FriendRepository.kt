package one.t10o.cheering_rocket.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import one.t10o.cheering_rocket.data.model.FriendRequest
import one.t10o.cheering_rocket.data.model.FriendRequestStatus
import one.t10o.cheering_rocket.data.model.Friendship
import one.t10o.cheering_rocket.data.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * フレンド機能に関するリポジトリ
 * フレンド検索、申請送信、申請管理を担当
 */
@Singleton
class FriendRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    /**
     * アカウントIDでユーザーを検索
     * @param accountId 検索するアカウントID
     * @return 見つかったユーザー、見つからない場合はnull
     */
    suspend fun searchUserByAccountId(accountId: String): Result<UserProfile?> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ログインしていません"))
            
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("accountId", accountId.uppercase())
                .limit(1)
                .get()
                .await()
            
            if (querySnapshot.isEmpty) {
                return Result.success(null)
            }
            
            val doc = querySnapshot.documents.first()
            val uid = doc.getString("uid") ?: return Result.success(null)
            
            // 自分自身は検索結果から除外
            if (uid == currentUserId) {
                return Result.failure(Exception("自分自身を検索することはできません"))
            }
            
            val userProfile = UserProfile(
                uid = uid,
                displayName = doc.getString("displayName") ?: "名前なし",
                accountId = doc.getString("accountId") ?: "",
                photoUrl = doc.getString("photoUrl")
            )
            
            Result.success(userProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * フレンド申請を送信
     * @param toUser 申請先のユーザー
     * @return 成功/失敗
     */
    suspend fun sendFriendRequest(toUser: UserProfile): Result<Unit> {
        return try {
            val currentUser = firebaseAuth.currentUser
                ?: return Result.failure(Exception("ログインしていません"))
            
            // 現在のユーザー情報を取得
            val currentUserDoc = firestore.collection("users")
                .document(currentUser.uid)
                .get()
                .await()
            
            val fromUserName = currentUserDoc.getString("displayName") ?: "名前なし"
            val fromUserPhotoUrl = currentUserDoc.getString("photoUrl")
            
            // 既存の申請をチェック（相互方向）
            val existingRequests = firestore.collection("friendRequests")
                .whereEqualTo("fromUserId", currentUser.uid)
                .whereEqualTo("toUserId", toUser.uid)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .get()
                .await()
            
            if (!existingRequests.isEmpty) {
                return Result.failure(Exception("既にフレンド申請を送信しています"))
            }
            
            // 逆方向の申請もチェック（相手からの申請が既にある場合）
            val reverseRequests = firestore.collection("friendRequests")
                .whereEqualTo("fromUserId", toUser.uid)
                .whereEqualTo("toUserId", currentUser.uid)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .get()
                .await()
            
            if (!reverseRequests.isEmpty) {
                return Result.failure(Exception("相手から既にフレンド申請が届いています。「フレンド申請」画面で確認してください"))
            }
            
            // 既にフレンドかチェック
            val existingFriendship = checkExistingFriendship(currentUser.uid, toUser.uid)
            if (existingFriendship) {
                return Result.failure(Exception("既にフレンドです"))
            }
            
            // フレンド申請を作成
            val requestDoc = hashMapOf(
                "fromUserId" to currentUser.uid,
                "fromUserName" to fromUserName,
                "fromUserPhotoUrl" to fromUserPhotoUrl,
                "toUserId" to toUser.uid,
                "toUserName" to toUser.displayName,
                "toUserPhotoUrl" to toUser.photoUrl,
                "status" to FriendRequestStatus.PENDING.name,
                "createdAt" to Timestamp.now()
            )
            
            firestore.collection("friendRequests")
                .add(requestDoc)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 既にフレンドかどうかをチェック
     */
    private suspend fun checkExistingFriendship(userId1: String, userId2: String): Boolean {
        // friendshipsコレクションで双方向のチェック
        val friendship1 = firestore.collection("friendships")
            .whereEqualTo("userId1", userId1)
            .whereEqualTo("userId2", userId2)
            .limit(1)
            .get()
            .await()
        
        if (!friendship1.isEmpty) return true
        
        val friendship2 = firestore.collection("friendships")
            .whereEqualTo("userId1", userId2)
            .whereEqualTo("userId2", userId1)
            .limit(1)
            .get()
            .await()
        
        return !friendship2.isEmpty
    }
    
    /**
     * 受信したフレンド申請一覧をリアルタイムで取得
     */
    fun getReceivedFriendRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("friendRequests")
            .whereEqualTo("toUserId", currentUserId)
            .whereEqualTo("status", FriendRequestStatus.PENDING.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val requests = snapshot?.documents?.mapNotNull { doc ->
                    FriendRequest(
                        id = doc.id,
                        fromUserId = doc.getString("fromUserId") ?: "",
                        fromUserName = doc.getString("fromUserName") ?: "名前なし",
                        fromUserPhotoUrl = doc.getString("fromUserPhotoUrl"),
                        toUserId = doc.getString("toUserId") ?: "",
                        toUserName = doc.getString("toUserName") ?: "",
                        toUserPhotoUrl = doc.getString("toUserPhotoUrl"),
                        status = FriendRequestStatus.valueOf(
                            doc.getString("status") ?: FriendRequestStatus.PENDING.name
                        ),
                        createdAt = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()
                
                trySend(requests)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * 保留中のフレンド申請数を取得
     */
    fun getPendingRequestCount(): Flow<Int> = callbackFlow {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            trySend(0)
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("friendRequests")
            .whereEqualTo("toUserId", currentUserId)
            .whereEqualTo("status", FriendRequestStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * フレンド申請を承認
     * フレンド関係を作成し、申請ステータスを更新
     */
    suspend fun acceptFriendRequest(request: FriendRequest): Result<Unit> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ログインしていません"))
            
            // 申請が自分宛てか確認
            if (request.toUserId != currentUserId) {
                return Result.failure(Exception("この申請を承認する権限がありません"))
            }
            
            // フレンド関係を作成
            val friendshipDoc = hashMapOf(
                "userId1" to request.fromUserId,
                "userId2" to request.toUserId,
                "user1Name" to request.fromUserName,
                "user2Name" to request.toUserName,
                "user1PhotoUrl" to request.fromUserPhotoUrl,
                "user2PhotoUrl" to request.toUserPhotoUrl,
                "createdAt" to Timestamp.now()
            )
            
            firestore.collection("friendships")
                .add(friendshipDoc)
                .await()
            
            // 申請ステータスを更新
            firestore.collection("friendRequests")
                .document(request.id)
                .update("status", FriendRequestStatus.ACCEPTED.name)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * フレンド申請を拒否
     */
    suspend fun rejectFriendRequest(request: FriendRequest): Result<Unit> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ログインしていません"))
            
            // 申請が自分宛てか確認
            if (request.toUserId != currentUserId) {
                return Result.failure(Exception("この申請を拒否する権限がありません"))
            }
            
            // 申請ステータスを更新
            firestore.collection("friendRequests")
                .document(request.id)
                .update("status", FriendRequestStatus.REJECTED.name)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * フレンド一覧をリアルタイムで取得
     * 双方向のフレンド関係を統合して返す
     */
    fun getFriends(): Flow<List<Friendship>> = callbackFlow {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        // 両方向のスナップショットを保持
        var snapshot1Data: List<Friendship> = emptyList()
        var snapshot2Data: List<Friendship> = emptyList()
        
        fun emitCombinedData() {
            val combined = (snapshot1Data + snapshot2Data)
                .sortedByDescending { it.createdAt }
            trySend(combined)
        }
        
        // userId1として登録されているフレンド関係を監視
        val listener1 = firestore.collection("friendships")
            .whereEqualTo("userId1", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                
                snapshot1Data = snapshot?.documents?.mapNotNull { doc ->
                    Friendship(
                        id = doc.id,
                        friendUserId = doc.getString("userId2") ?: "",
                        friendUserName = doc.getString("user2Name") ?: "名前なし",
                        friendPhotoUrl = doc.getString("user2PhotoUrl"),
                        createdAt = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()
                
                emitCombinedData()
            }
        
        // userId2として登録されているフレンド関係を監視
        val listener2 = firestore.collection("friendships")
            .whereEqualTo("userId2", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                
                snapshot2Data = snapshot?.documents?.mapNotNull { doc ->
                    Friendship(
                        id = doc.id,
                        friendUserId = doc.getString("userId1") ?: "",
                        friendUserName = doc.getString("user1Name") ?: "名前なし",
                        friendPhotoUrl = doc.getString("user1PhotoUrl"),
                        createdAt = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()
                
                emitCombinedData()
            }
        
        awaitClose {
            listener1.remove()
            listener2.remove()
        }
    }
    
    /**
     * フレンド関係を削除
     * @param friendship 削除するフレンド関係
     */
    suspend fun deleteFriendship(friendship: Friendship): Result<Unit> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ログインしていません"))
            
            // フレンド関係のドキュメントを削除
            firestore.collection("friendships")
                .document(friendship.id)
                .delete()
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

