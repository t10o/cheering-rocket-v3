package one.t10o.cheering_rocket.ui.screen.event

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import one.t10o.cheering_rocket.data.model.Event
import one.t10o.cheering_rocket.data.model.EventInvitation
import one.t10o.cheering_rocket.data.model.EventStatus
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * イベント詳細画面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onNavigateToInvite: () -> Unit,
    onStartRun: () -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    
    // 削除後に画面を閉じる
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }
    
    // エラーメッセージ
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    
    // 成功メッセージ
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSuccess()
        }
    }
    
    // 削除確認ダイアログ
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("イベントを削除") },
            text = { Text("このイベントを削除しますか？\n参加者の招待も全て削除されます。\n\nこの操作は取り消せません。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteEvent()
                    }
                ) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
    
    // 脱退確認ダイアログ
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("イベントから脱退") },
            text = { Text("このイベントから脱退しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        viewModel.leaveEvent()
                    }
                ) {
                    Text("脱退", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("イベント詳細") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                },
                actions = {
                    if (uiState.isOwner) {
                        IconButton(onClick = onNavigateToEdit) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "編集"
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "削除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.event == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("イベントが見つかりません")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        EventInfoCard(event = uiState.event!!)
                    }
                    
                    // 招待への応答（承認待ちの場合）
                    if (uiState.isPendingInvitation) {
                        item {
                            InvitationResponseCard(
                                isProcessing = uiState.isProcessing,
                                onAccept = { viewModel.acceptInvitation() },
                                onReject = { viewModel.rejectInvitation() }
                            )
                        }
                    }
                    
                    // メンバー一覧
                    item {
                        MembersCard(
                            event = uiState.event!!,
                            members = uiState.members,
                            isOwner = uiState.isOwner,
                            onNavigateToInvite = onNavigateToInvite
                        )
                    }
                    
                    // 応援URL
                    item {
                        ShareUrlCard(
                            shareUrl = uiState.shareUrl,
                            onCopy = {
                                copyToClipboard(context, uiState.shareUrl)
                                // snackbar表示はViewModelからの方が良いが、一旦ここで
                            }
                        )
                    }
                    
                    // アクションボタン
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // オーナーまたはメンバーのみラン開始可能
                            if (uiState.isOwner || uiState.isMember) {
                                Button(
                                    onClick = onStartRun,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = uiState.event?.status != EventStatus.FINISHED
                                ) {
                                    Text(
                                        text = when (uiState.event?.status) {
                                            EventStatus.RUNNING -> "ランに参加する"
                                            EventStatus.FINISHED -> "終了したイベントです"
                                            else -> "ランを開始する"
                                        },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            
                            // メンバーの場合は脱退ボタン
                            if (uiState.isMember && !uiState.isOwner) {
                                OutlinedButton(
                                    onClick = { showLeaveDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("イベントから脱退")
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventInfoCard(event: Event) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall
                )
                EventStatusBadge(status = event.status)
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatTimestamp(event.startDateTime),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            if (event.description.isNotBlank()) {
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "主催: ${event.ownerName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun InvitationResponseCard(
    isProcessing: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "📩 このイベントに招待されています",
                style = MaterialTheme.typography.titleMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing
                ) {
                    Text("拒否")
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("参加する")
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersCard(
    event: Event,
    members: List<EventInvitation>,
    isOwner: Boolean,
    onNavigateToInvite: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "メンバー",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Badge {
                        Text("${members.size + 1}")  // オーナー含む
                    }
                }
                if (isOwner) {
                    IconButton(onClick = onNavigateToInvite) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "メンバーを招待"
                        )
                    }
                }
            }
            
            // オーナー
            MemberItem(
                name = "${event.ownerName}（オーナー）",
                photoUrl = null,  // TODO: オーナーの写真
                isOwner = true
            )
            
            // メンバー
            members.forEach { member ->
                MemberItem(
                    name = member.invitedUserName,
                    photoUrl = null,  // TODO: メンバーの写真
                    isOwner = false
                )
            }
        }
    }
}

@Composable
private fun MemberItem(
    name: String,
    photoUrl: String?,
    isOwner: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isOwner) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ShareUrlCard(
    shareUrl: String,
    onCopy: () -> Unit
) {
    OutlinedButton(
        onClick = onCopy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text("応援URLをコピー")
    }
}

@Composable
private fun EventStatusBadge(status: EventStatus) {
    val (text, containerColor) = when (status) {
        EventStatus.UPCOMING -> "開催前" to MaterialTheme.colorScheme.outline
        EventStatus.RUNNING -> "開催中" to MaterialTheme.colorScheme.primary
        EventStatus.FINISHED -> "終了" to MaterialTheme.colorScheme.outline
    }
    
    Badge(containerColor = containerColor) {
        Text(text)
    }
}

private fun formatTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return "日時未設定"
    val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)
    return dateFormat.format(timestamp.toDate())
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("応援URL", text)
    clipboard.setPrimaryClip(clip)
}
