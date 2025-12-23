package one.t10o.cheering_rocket.data.model

import com.google.firebase.Timestamp

/**
 * イベントを表すデータクラス
 */
data class Event(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val startDateTime: Timestamp? = null,
    val ownerId: String = "",
    val ownerName: String = "",
    val status: EventStatus = EventStatus.UPCOMING,
    val shareToken: String = "",  // 応援用URL用のトークン
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/**
 * イベントのステータス
 */
enum class EventStatus {
    UPCOMING,   // 開催前
    RUNNING,    // 開催中（ラン実行中）
    FINISHED    // 終了
}

/**
 * イベント招待を表すデータクラス
 */
data class EventInvitation(
    val id: String = "",
    val eventId: String = "",
    val eventTitle: String = "",
    val eventStartDateTime: Timestamp? = null,
    val invitedUserId: String = "",
    val invitedUserName: String = "",
    val inviterUserId: String = "",
    val inviterUserName: String = "",
    val status: InvitationStatus = InvitationStatus.PENDING,
    val createdAt: Timestamp? = null
)

/**
 * 招待のステータス
 */
enum class InvitationStatus {
    PENDING,    // 保留中
    ACCEPTED,   // 承認済み（メンバー）
    REJECTED    // 拒否済み
}

