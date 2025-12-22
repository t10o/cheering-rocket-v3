package one.t10o.cheering_rocket.ui.screen.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * ログイン画面
 * Google認証でサインインする
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (isNewUser: Boolean) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ログイン",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Cheering Rocket",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "ランナーを応援しよう！",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // TODO: 実際のGoogle認証ボタンに置き換え
            Button(
                onClick = { onLoginSuccess(true) }, // 仮: 新規ユーザーとして遷移
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Googleでログイン")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // デバッグ用: 既存ユーザーとしてログイン
            Button(
                onClick = { onLoginSuccess(false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("（デバッグ）既存ユーザーでログイン")
            }
        }
    }
}

