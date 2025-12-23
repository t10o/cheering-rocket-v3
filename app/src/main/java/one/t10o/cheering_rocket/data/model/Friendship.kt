package one.t10o.cheering_rocket.data.model

import com.google.firebase.Timestamp

/**
 * フレンド関係を表すデータクラス
 */
data class Friendship(
    val id: String = "",
    val friendUserId: String = "",
    val friendUserName: String = "",
    val friendPhotoUrl: String? = null,
    val createdAt: Timestamp? = null
)

