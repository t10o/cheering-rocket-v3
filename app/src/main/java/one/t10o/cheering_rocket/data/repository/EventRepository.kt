package one.t10o.cheering_rocket.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import one.t10o.cheering_rocket.data.model.Event
import one.t10o.cheering_rocket.data.model.EventInvitation
import one.t10o.cheering_rocket.data.model.EventStatus
import one.t10o.cheering_rocket.data.model.InvitationStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * イベント機能に関するリポジトリ
 */
@Singleton
class EventRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    /**
     * イベントを作成
     */
    suspend fun createEvent(
        title: String,
        description: String,
        startDateTime: Timestamp
    ): Result<String> {
        return try {
            val currentUser = firebaseAuth.currentUser
                ?: return Result.failure(Exception("ログインしていません"))
            
            // ユーザー情報を取得
            val userDoc = firestore.collection("users")
                .document(currentUser.uid)
                .get()
                .await()
            
            val ownerName = userDoc.getString("displayName") ?: "名前なし"
            
            // 共有用トークンを生成
            val shareToken = UUID.randomUUID().toString().replace("-", "").take(12)
            
            val eventDoc = hashMapOf(
                "title" to title,
                "description" to description,
                "startDateTime" to startDateTime,
                "ownerId" to currentUser.uid,
                "ownerName" to ownerName,
                "status" to EventStatus.UPCOMING.name,
                "shareToken" to shareToken,
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )
            
            val docRef = firestore.collection("events")
                .add(eventDoc)
                .await()
            
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * イベントを更新（オーナーのみ）
     */
    suspend fun updateEvent(
        eventId: String,
        title: String,
        description: String,
        startDateTime: Timestamp
    ): Result<Unit> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ログインしていません"))
            
            // オーナー確認
            val eventDoc = firestore.collection("events")
                .document(eventId)
                .get()
                .await()
            
            if (eventDoc.getString("ownerId") != currentUserId) {
                return Result.failure(Exception("編集権限がありません"))
            }
            
            val updates = hashMapOf<String, Any>(
                "title" to title,
                "description" to description,
                "startDateTime" to startDateTime,
                "updatedAt" to Timestamp.now()
            )
            
            firestore.collection("events")
                .document(eventId)
                .update(updates)
                .await()
            
            // 関連する招待のイベント情報も更新
            val invitations = firestore.collection("eventInvitations")
                .whereEqualTo("eventId", eventId)
                .get()
                .await()
            
            for (invitation in invitations.documents) {
                invitation.reference.update(
                    mapOf(
                        "eventTitle" to title,
                        "eventStartDateTime" to startDateTime
                    )
                ).await()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 自分が作成したイベント一覧を取得
     */
    fun getMyEvents(): Flow<List<Event>> = callbackFlow {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("events")
            .whereEqualTo("ownerId", currentUserId)
            .orderBy("startDateTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    Event(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        startDateTime = doc.getTimestamp("startDateTime"),
                        ownerId = doc.getString("ownerId") ?: "",
                        ownerName = doc.getString("ownerName") ?: "",
                        status = try {
                            EventStatus.valueOf(doc.getString("status") ?: EventStatus.UPCOMING.name)
                        } catch (e: Exception) {
                            EventStatus.UPCOMING
                        },
                        shareToken = doc.getString("shareToken") ?: "",
                        createdAt = doc.getTimestamp("createdAt"),
                        updatedAt = doc.getTimestamp("updatedAt")
                    )
                } ?: emptyList()
                
                trySend(events)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * 招待されているイベント一覧を取得（承認待ち）
     */
    fun getPendingInvitations(): Flow<List<EventInvitation>> = callbackFlow {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("eventInvitations")
            .whereEqualTo("invitedUserId", currentUserId)
            .whereEqualTo("status", InvitationStatus.PENDING.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val invitations = snapshot?.documents?.mapNotNull { doc ->
                    EventInvitation(
                        id = doc.id,
                        eventId = doc.getString("eventId") ?: "",
                        eventTitle = doc.getString("eventTitle") ?: "",
                        eventStartDateTime = doc.getTimestamp("eventStartDateTime"),
                        invitedUserId = doc.getString("invitedUserId") ?: "",
                        invitedUserName = doc.getString("invitedUserName") ?: "",
                        inviterUserId = doc.getString("inviterUserId") ?: "",
                        inviterUserName = doc.getString("inviterUserName") ?: "",
                        status = InvitationStatus.PENDING,
                        createdAt = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()
                
                trySend(invitations)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * 参加中のイベント一覧を取得（承認済み招待）
     */
    fun getJoinedEvents(): Flow<List<EventInvitation>> = callbackFlow {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("eventInvitations")
            .whereEqualTo("invitedUserId", currentUserId)
            .whereEqualTo("status", InvitationStatus.ACCEPTED.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val invitations = snapshot?.documents?.mapNotNull { doc ->
                    EventInvitation(
                        id = doc.id,
                        eventId = doc.getString("eventId") ?: "",
                        eventTitle = doc.getString("eventTitle") ?: "",
                        eventStartDateTime = doc.getTimestamp("eventStartDateTime"),
                        invitedUserId = doc.getString("invitedUserId") ?: "",
                        invitedUserName = doc.getString("invitedUserName") ?: "",
                        inviterUserId = doc.getString("inviterUserId") ?: "",
                        inviterUserName = doc.getString("inviterUserName") ?: "",
                        status = InvitationStatus.ACCEPTED,
                        createdAt = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()
                
                trySend(invitations)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * イベント詳細を取得
     */
    suspend fun getEvent(eventId: String): Result<Event> {
        return try {
            val doc = firestore.collection("events")
                .document(eventId)
                .get()
                .await()
            
            if (!doc.exists()) {
                return Result.failure(Exception("イベントが見つかりません"))
            }
            
            val event = Event(
                id = doc.id,
                title = doc.getString("title") ?: "",
                description = doc.getString("description") ?: "",
                startDateTime = doc.getTimestamp("startDateTime"),
                ownerId = doc.getString("ownerId") ?: "",
                ownerName = doc.getString("ownerName") ?: "",
                status = try {
                    EventStatus.valueOf(doc.getString("status") ?: EventStatus.UPCOMING.name)
                } catch (e: Exception) {
                    EventStatus.UPCOMING
                },
                shareToken = doc.getString("shareToken") ?: "",
                createdAt = doc.getTimestamp("createdAt"),
                updatedAt = doc.getTimestamp("updatedAt")
            )
            
            Result.success(event)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * イベントをリアルタイムで監視
     */
    fun observeEvent(eventId: String): Flow<Event?> = callbackFlow {
        val listener = firestore.collection("events")
            .document(eventId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                
                val event = Event(
                    id = snapshot.id,
                    title = snapshot.getString("title") ?: "",
                    description = snapshot.getString("description") ?: "",
                    startDateTime = snapshot.getTimestamp("startDateTime"),
                    ownerId = snapshot.getString("ownerId") ?: "",
                    ownerName = snapshot.getString("ownerName") ?: "",
                    status = try {
                        EventStatus.valueOf(snapshot.getString("status") ?: EventStatus.UPCOMING.name)
                    } catch (e: Exception) {
                        EventStatus.UPCOMING
                    },
                    shareToken = snapshot.getString("shareToken") ?: "",
                    createdAt = snapshot.getTimestamp("createdAt"),
                    updatedAt = snapshot.getTimestamp("updatedAt")
                )
                
                trySend(event)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * イベントのメンバー一覧を取得（承認済み招待）
     */
    fun getEventMembers(eventId: String): Flow<List<EventInvitation>> = callbackFlow {
        val listener = firestore.collection("eventInvitations")
            .whereEqualTo("eventId", eventId)
            .whereEqualTo("status", InvitationStatus.ACCEPTED.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val members = snapshot?.documents?.mapNotNull { doc ->
                    EventInvitation(
                        id = doc.id,
                        eventId = doc.getString("eventId") ?: "",
                        eventTitle = doc.getString("eventTitle") ?: "",
                        eventStartDateTime = doc.getTimestamp("eventStartDateTime"),
                        invitedUserId = doc.getString("invitedUserId") ?: "",
                        invitedUserName = doc.getString("invitedUserName") ?: "",
                        inviterUserId = doc.getString("inviterUserId") ?: "",
                        inviterUserName = doc.getString("inviterUserName") ?: "",
                        status = InvitationStatus.ACCEPTED,
                        createdAt = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()
                
                trySend(members)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * 自分のこのイベントへの招待状態を取得
     */
    suspend fun getMyInvitation(eventId: String): Result<EventInvitation?> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ログインしていません"))
            
            val snapshot = firestore.collection("eventInvitations")
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("invitedUserId", currentUserId)
                .limit(1)
                .get()
                .await()
            
            if (snapshot.isEmpty) {
                return Result.success(null)
            }
            
            val doc = snapshot.documents.first()
            val invitation = EventInvitation(
                id = doc.id,
                eventId = doc.getString("eventId") ?: "",
                eventTitle = doc.getString("eventTitle") ?: "",
                eventStartDateTime = doc.getTimestamp("eventStartDateTime"),
                invitedUserId = doc.getString("invitedUserId") ?: "",
                invitedUserName = doc.getString("invitedUserName") ?: "",
                inviterUserId = doc.getString("inviterUserId") ?: "",
                inviterUserName = doc.getString("inviterUserName") ?: "",
                status = try {
                    InvitationStatus.valueOf(doc.getString("status") ?: InvitationStatus.PENDING.name)
                } catch (e: Exception) {
                    InvitationStatus.PENDING
                },
                createdAt = doc.getTimestamp("createdAt")
            )
            
            Result.success(invitation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 招待を承認
     */
    suspend fun acceptInvitation(invitationId: String): Result<Unit> {
        return try {
            firestore.collection("eventInvitations")
                .document(invitationId)
                .update("status", InvitationStatus.ACCEPTED.name)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 招待を拒否
     */
    suspend fun rejectInvitation(invitationId: String): Result<Unit> {
        return try {
            firestore.collection("eventInvitations")
                .document(invitationId)
                .update("status", InvitationStatus.REJECTED.name)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * イベントから脱退（招待を削除）
     */
    suspend fun leaveEvent(invitationId: String): Result<Unit> {
        return try {
            firestore.collection("eventInvitations")
                .document(invitationId)
                .delete()
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * イベントを削除（オーナーのみ）
     */
    suspend fun deleteEvent(eventId: String): Result<Unit> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("ログインしていません"))
            
            // イベントを取得してオーナー確認
            val eventDoc = firestore.collection("events")
                .document(eventId)
                .get()
                .await()
            
            if (eventDoc.getString("ownerId") != currentUserId) {
                return Result.failure(Exception("削除権限がありません"))
            }
            
            // 関連する招待も削除
            val invitations = firestore.collection("eventInvitations")
                .whereEqualTo("eventId", eventId)
                .get()
                .await()
            
            for (invitation in invitations.documents) {
                invitation.reference.delete().await()
            }
            
            // イベントを削除
            firestore.collection("events")
                .document(eventId)
                .delete()
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 現在のユーザーIDを取得
     */
    fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
    
    /**
     * フレンドをイベントに招待
     */
    suspend fun inviteToEvent(
        eventId: String,
        friendUserId: String,
        friendUserName: String
    ): Result<Unit> {
        return try {
            val currentUser = firebaseAuth.currentUser
                ?: return Result.failure(Exception("ログインしていません"))
            
            // イベント情報を取得
            val eventDoc = firestore.collection("events")
                .document(eventId)
                .get()
                .await()
            
            if (!eventDoc.exists()) {
                return Result.failure(Exception("イベントが見つかりません"))
            }
            
            // オーナー確認
            if (eventDoc.getString("ownerId") != currentUser.uid) {
                return Result.failure(Exception("招待権限がありません"))
            }
            
            // 既に招待済みかチェック
            val existingInvitation = firestore.collection("eventInvitations")
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("invitedUserId", friendUserId)
                .limit(1)
                .get()
                .await()
            
            if (!existingInvitation.isEmpty) {
                return Result.failure(Exception("既に招待済みです"))
            }
            
            // 現在のユーザー情報を取得
            val userDoc = firestore.collection("users")
                .document(currentUser.uid)
                .get()
                .await()
            
            val inviterUserName = userDoc.getString("displayName") ?: "名前なし"
            
            // 招待を作成
            val invitationDoc = hashMapOf(
                "eventId" to eventId,
                "eventTitle" to (eventDoc.getString("title") ?: ""),
                "eventStartDateTime" to eventDoc.getTimestamp("startDateTime"),
                "invitedUserId" to friendUserId,
                "invitedUserName" to friendUserName,
                "inviterUserId" to currentUser.uid,
                "inviterUserName" to inviterUserName,
                "status" to InvitationStatus.PENDING.name,
                "createdAt" to Timestamp.now()
            )
            
            firestore.collection("eventInvitations")
                .add(invitationDoc)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * イベントに既に招待済みのユーザーID一覧を取得
     */
    suspend fun getInvitedUserIds(eventId: String): Result<List<String>> {
        return try {
            val snapshot = firestore.collection("eventInvitations")
                .whereEqualTo("eventId", eventId)
                .get()
                .await()
            
            val userIds = snapshot.documents.mapNotNull { doc ->
                doc.getString("invitedUserId")
            }
            
            Result.success(userIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

