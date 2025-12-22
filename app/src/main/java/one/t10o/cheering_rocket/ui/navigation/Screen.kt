package one.t10o.cheering_rocket.ui.navigation

/**
 * アプリ内の画面を定義
 * ナビゲーションルートとして使用
 */
sealed class Screen(val route: String) {
    // 認証フロー
    data object Login : Screen("login")
    data object ProfileSetup : Screen("profile_setup")
    
    // メインタブ（Bottom Navigation）
    data object Home : Screen("home")
    data object Events : Screen("events")
    data object Friends : Screen("friends")
    data object Profile : Screen("profile")
    
    // イベント関連
    data object EventCreate : Screen("event_create")
    data object EventDetail : Screen("event_detail/{eventId}") {
        fun createRoute(eventId: String) = "event_detail/$eventId"
    }
    data object EventEdit : Screen("event_edit/{eventId}") {
        fun createRoute(eventId: String) = "event_edit/$eventId"
    }
    data object EventInvite : Screen("event_invite/{eventId}") {
        fun createRoute(eventId: String) = "event_invite/$eventId"
    }
    
    // フレンド関連
    data object FriendSearch : Screen("friend_search")
    data object FriendRequests : Screen("friend_requests")
    
    // ラン関連
    data object Run : Screen("run/{eventId}") {
        fun createRoute(eventId: String) = "run/$eventId"
    }
    data object RunEnd : Screen("run_end/{eventId}") {
        fun createRoute(eventId: String) = "run_end/$eventId"
    }
    
    // プロフィール編集
    data object ProfileEdit : Screen("profile_edit")
}

