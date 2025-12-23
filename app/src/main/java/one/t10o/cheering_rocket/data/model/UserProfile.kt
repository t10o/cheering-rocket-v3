package one.t10o.cheering_rocket.data.model

/**
 * ユーザープロフィール情報（検索結果や表示用）
 */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val accountId: String,
    val photoUrl: String? = null
)

