package one.t10o.cheering_rocket.data.model

import com.google.firebase.Timestamp

/**
 * フレンド申請を表すデータクラス
 */
data class FriendRequest(
    val id: String = "",
    val fromUserId: String = "",
    val fromUserName: String = "",
    val fromUserPhotoUrl: String? = null,
    val toUserId: String = "",
    val toUserName: String = "",
    val toUserPhotoUrl: String? = null,
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
    val createdAt: Timestamp? = null
)

/**
 * フレンド申請のステータス
 */
enum class FriendRequestStatus {
    PENDING,   // 保留中
    ACCEPTED,  // 承認済み
    REJECTED   // 拒否済み
}

