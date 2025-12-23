package one.t10o.cheering_rocket.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.Timestamp
import one.t10o.cheering_rocket.data.model.Event
import one.t10o.cheering_rocket.data.model.EventInvitation
import one.t10o.cheering_rocket.data.model.EventStatus
import one.t10o.cheering_rocket.data.model.RunSession
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * ホーム画面
 * - 走行中のラン（継続を促す）
 * - 開催中/開催予定のイベント
 * - 保留中の招待
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToRun: (eventId: String) -> Unit,
    onNavigateToEventDetail: (eventId: String) -> Unit,
    onNavigateToEventCreate: () -> Unit = {},
    onNavigateToEvents: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // エラーメッセージ
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "🚀 Cheering Rocket",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "更新"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToEventCreate,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "イベント作成"
                )
            }
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
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 走行中のラン（最優先表示）
                    if (uiState.hasActiveRun) {
                        item {
                            ActiveRunSection(
                                runs = uiState.activeRuns,
                                onContinueRun = { run ->
                                    onNavigateToRun(run.eventId)
                                }
                            )
                        }
                    }
                    
                    // 保留中の招待
                    if (uiState.pendingInvitationCount > 0) {
                        item {
                            PendingInvitationsCard(
                                invitations = uiState.pendingInvitations,
                                onTap = { invitation ->
                                    onNavigateToEventDetail(invitation.eventId)
                                }
                            )
                        }
                    }
                    
                    // イベント一覧
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📅 イベント",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.upcomingEvents.isNotEmpty()) {
                                Text(
                                    text = "すべて見る →",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { onNavigateToEvents() }
                                )
                            }
                        }
                    }
                    
                    if (uiState.upcomingEvents.isEmpty()) {
                        item {
                            EmptyEventsCard(onCreateEvent = onNavigateToEventCreate)
                        }
                    } else {
                        items(
                            items = uiState.upcomingEvents.take(5),  // 最大5件
                            key = { it.event.id }
                        ) { eventItem ->
                            EventCard(
                                eventItem = eventItem,
                                onTap = { onNavigateToEventDetail(eventItem.event.id) },
                                onStartRun = { onNavigateToRun(eventItem.event.id) }
                            )
                        }
                    }
                    
                    // 下部余白
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveRunSection(
    runs: List<RunSession>,
    onContinueRun: (RunSession) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "🏃 走行中",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        runs.forEach { run ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onContinueRun(run) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ランを継続中",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "距離: ${formatDistance(run.totalDistanceMeters)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        run.startedAt?.let { startTime ->
                            Text(
                                text = "開始: ${formatTimestamp(startTime)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    Button(onClick = { onContinueRun(run) }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("続ける")
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingInvitationsCard(
    invitations: List<EventInvitation>,
    onTap: (EventInvitation) -> Unit
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BadgedBox(
                    badge = {
                        Badge { Text("${invitations.size}") }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Mail,
                        contentDescription = null
                    )
                }
                Text(
                    text = "招待が届いています",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            invitations.take(3).forEach { invitation ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTap(invitation) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = invitation.eventTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${invitation.inviterUserName} さんから",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = "確認 →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            if (invitations.size > 3) {
                Text(
                    text = "他 ${invitations.size - 3} 件",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun EventCard(
    eventItem: HomeEventItem,
    onTap: () -> Unit,
    onStartRun: () -> Unit
) {
    val event = eventItem.event
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        colors = CardDefaults.cardColors(
            containerColor = when (event.status) {
                EventStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
                EventStatus.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant
                EventStatus.FINISHED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
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
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    EventStatusBadge(status = event.status)
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatTimestamp(event.startDateTime),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            if (eventItem.isOwner) {
                Text(
                    text = "👑 あなたが作成",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "主催: ${event.ownerName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 開催中または開催前のイベントはラン開始ボタンを表示
            if (event.status != EventStatus.FINISHED) {
                Button(
                    onClick = onStartRun,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (event.status) {
                            EventStatus.RUNNING -> "ランに参加"
                            else -> "ランを開始"
                        }
                    )
                }
            }
        }
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

@Composable
private fun EmptyEventsCard(onCreateEvent: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🎉",
                style = MaterialTheme.typography.displayMedium
            )
            Text(
                text = "イベントがありません",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "イベントを作成してフレンドを招待しましょう！",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onCreateEvent) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("イベントを作成")
            }
        }
    }
}

private fun formatTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return "日時未設定"
    val dateFormat = SimpleDateFormat("M/d (E) HH:mm", Locale.JAPAN)
    return dateFormat.format(timestamp.toDate())
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000) {
        String.format("%.2f km", meters / 1000)
    } else {
        String.format("%.0f m", meters)
    }
}
