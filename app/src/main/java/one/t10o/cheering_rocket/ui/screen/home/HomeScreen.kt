package one.t10o.cheering_rocket.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * ホーム画面
 * - 参加中イベントの状況表示
 * - クイックアクションへの導線
 */
@Composable
fun HomeScreen(
    onNavigateToRun: (eventId: String) -> Unit,
    onNavigateToEventDetail: (eventId: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ホーム",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 現在参加中のイベント
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "参加中のイベント",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "現在参加中のイベントはありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 今後のイベント
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
                Text(
                    text = "今後のイベント",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "予定されているイベントはありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // クイックアクション（デバッグ用）
        Text(
            text = "デバッグ用ナビゲーション",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { onNavigateToEventDetail("sample-event-id") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("サンプルイベント詳細へ")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { onNavigateToRun("sample-event-id") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ラン画面へ（デバッグ）")
        }
    }
}

