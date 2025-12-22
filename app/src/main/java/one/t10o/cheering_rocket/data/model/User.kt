package one.t10o.cheering_rocket.data.model

/**
 * ユーザー情報を表すデータクラス
 */
data class User(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isNewUser: Boolean = false,
    val accountId: String? = null // 共有用ID（Firestoreで管理）
)

