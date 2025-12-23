package one.t10o.cheering_rocket.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import one.t10o.cheering_rocket.ui.screen.auth.AuthViewModel
import one.t10o.cheering_rocket.ui.screen.auth.LoginScreen
import one.t10o.cheering_rocket.ui.screen.auth.ProfileSetupScreen
import one.t10o.cheering_rocket.ui.screen.event.EventCreateScreen
import one.t10o.cheering_rocket.ui.screen.event.EventDetailScreen
import one.t10o.cheering_rocket.ui.screen.event.EventEditScreen
import one.t10o.cheering_rocket.ui.screen.event.EventInviteScreen
import one.t10o.cheering_rocket.ui.screen.event.EventListScreen
import one.t10o.cheering_rocket.ui.screen.friend.FriendListScreen
import one.t10o.cheering_rocket.ui.screen.friend.FriendRequestsScreen
import one.t10o.cheering_rocket.ui.screen.friend.FriendSearchScreen
import one.t10o.cheering_rocket.ui.screen.home.HomeScreen
import one.t10o.cheering_rocket.ui.screen.profile.ProfileEditScreen
import one.t10o.cheering_rocket.ui.screen.profile.ProfileScreen
import one.t10o.cheering_rocket.ui.screen.run.PhotoCaptureScreen
import one.t10o.cheering_rocket.ui.screen.run.RunEndScreen
import one.t10o.cheering_rocket.ui.screen.run.RunScreen

/**
 * Bottom Navigation のアイテム定義
 */
data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        screen = Screen.Home,
        label = "ホーム",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    BottomNavItem(
        screen = Screen.Events,
        label = "イベント",
        selectedIcon = Icons.Filled.Event,
        unselectedIcon = Icons.Outlined.Event
    ),
    BottomNavItem(
        screen = Screen.Friends,
        label = "フレンド",
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People
    ),
    BottomNavItem(
        screen = Screen.Profile,
        label = "プロフィール",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
)

/**
 * Bottom Navigation を表示するかどうか判定
 */
private fun shouldShowBottomBar(currentRoute: String?): Boolean {
    val mainRoutes = listOf(
        Screen.Home.route,
        Screen.Events.route,
        Screen.Friends.route,
        Screen.Profile.route
    )
    return currentRoute in mainRoutes
}

/**
 * アプリ全体のナビゲーション
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // 認証状態を確認
    val authViewModel: AuthViewModel = hiltViewModel()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    var isCheckingProfile by remember { mutableStateOf(true) }
    var isProfileCompleted by remember { mutableStateOf(false) }
    var startDestination by remember { mutableStateOf<String?>(null) }
    
    // 初期化時に認証状態とプロフィール完了状態を確認
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            try {
                isProfileCompleted = authViewModel.isProfileCompleted()
                startDestination = if (isProfileCompleted) {
                    Screen.Home.route
                } else {
                    Screen.ProfileSetup.route
                }
            } catch (e: Exception) {
                // 認証エラー（アカウント削除等）の場合はログアウト
                authViewModel.signOut()
                startDestination = Screen.Login.route
            }
        } else {
            startDestination = Screen.Login.route
        }
        isCheckingProfile = false
    }
    
    // 初期化中はローディング表示
    if (isCheckingProfile || startDestination == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    Scaffold(
        bottomBar = {
            if (shouldShowBottomBar(currentRoute)) {
                BottomNavigationBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination!!,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 認証フロー
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = { isNewUser ->
                        if (isNewUser) {
                            navController.navigate(Screen.ProfileSetup.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    }
                )
            }
            
            composable(Screen.ProfileSetup.route) {
                ProfileSetupScreen(
                    onSetupComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                        }
                    },
                    onLogout = {
                        authViewModel.signOut()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            
            // メインタブ
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToRun = { eventId ->
                        navController.navigate(Screen.Run.createRoute(eventId))
                    },
                    onNavigateToEventDetail = { eventId ->
                        navController.navigate(Screen.EventDetail.createRoute(eventId))
                    }
                )
            }
            
            composable(Screen.Events.route) {
                EventListScreen(
                    onNavigateToCreate = {
                        navController.navigate(Screen.EventCreate.route)
                    },
                    onNavigateToDetail = { eventId ->
                        navController.navigate(Screen.EventDetail.createRoute(eventId))
                    }
                )
            }
            
            composable(Screen.Friends.route) {
                FriendListScreen(
                    onNavigateToSearch = {
                        navController.navigate(Screen.FriendSearch.route)
                    },
                    onNavigateToRequests = {
                        navController.navigate(Screen.FriendRequests.route)
                    }
                )
            }
            
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToEdit = {
                        navController.navigate(Screen.ProfileEdit.route)
                    },
                    onLogout = {
                        authViewModel.signOut()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            
            // イベント関連
            composable(Screen.EventCreate.route) {
                EventCreateScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onEventCreated = { eventId ->
                        navController.navigate(Screen.EventDetail.createRoute(eventId)) {
                            popUpTo(Screen.Events.route)
                        }
                    }
                )
            }
            
            composable(
                route = Screen.EventDetail.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType })
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
                EventDetailScreen(
                    eventId = eventId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = {
                        navController.navigate(Screen.EventEdit.createRoute(eventId))
                    },
                    onNavigateToInvite = {
                        navController.navigate(Screen.EventInvite.createRoute(eventId))
                    },
                    onStartRun = {
                        navController.navigate(Screen.Run.createRoute(eventId))
                    }
                )
            }
            
            composable(
                route = Screen.EventEdit.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType })
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
                EventEditScreen(
                    eventId = eventId,
                    onNavigateBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            
            composable(
                route = Screen.EventInvite.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType })
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
                EventInviteScreen(
                    eventId = eventId,
                    onNavigateBack = { navController.popBackStack() },
                    onInviteSent = { navController.popBackStack() }
                )
            }
            
            // フレンド関連
            composable(Screen.FriendSearch.route) {
                FriendSearchScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(Screen.FriendRequests.route) {
                FriendRequestsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            // ラン関連
            composable(
                route = Screen.Run.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType })
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
                RunScreen(
                    eventId = eventId,
                    onNavigateToRunEnd = {
                        navController.navigate(Screen.RunEnd.createRoute(eventId))
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onTakePhoto = {
                        navController.navigate(Screen.PhotoCapture.createRoute(eventId))
                    }
                )
            }
            
            composable(
                route = Screen.RunEnd.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType })
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
                RunEndScreen(
                    eventId = eventId,
                    onNavigateBack = { navController.popBackStack() },
                    onFinished = {
                        // イベント詳細に戻る
                        navController.navigate(Screen.EventDetail.createRoute(eventId)) {
                            popUpTo(Screen.Run.createRoute(eventId)) { inclusive = true }
                        }
                    }
                )
            }
            
            composable(
                route = Screen.PhotoCapture.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType })
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
                PhotoCaptureScreen(
                    eventId = eventId,
                    onNavigateBack = { navController.popBackStack() },
                    onPhotoUploaded = { navController.popBackStack() }
                )
            }
            
            // プロフィール編集
            composable(Screen.ProfileEdit.route) {
                ProfileEditScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * Bottom Navigation Bar
 */
@Composable
private fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                selected = selected,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

