package one.t10o.cheering_rocket.ui.screen.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

/**
 * プロフィール初期設定画面
 * 初回ログイン後にユーザー名・アイコンを設定
 */
@Composable
fun ProfileSetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: ProfileSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val photoUrl by viewModel.photoUrl.collectAsState()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // UI状態の変化を監視
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ProfileSetupUiState.Success -> {
                onSetupComplete()
                viewModel.resetState()
            }
            is ProfileSetupUiState.Error -> {
                errorMessage = state.message
            }
            else -> {}
        }
    }

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
                text = "プロフィール設定",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "ようこそ！\nプロフィールを設定しましょう",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // プロフィール画像
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "プロフィール画像",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = "👤",
                    style = MaterialTheme.typography.displayLarge
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // TODO: 画像変更機能は後で実装
            TextButton(onClick = { /* TODO: 画像選択 */ }) {
                Text("アイコンを変更")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = userName,
                onValueChange = { viewModel.updateUserName(it) },
                label = { Text("ユーザー名") },
                placeholder = { Text("表示名を入力") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is ProfileSetupUiState.Loading
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            when (uiState) {
                is ProfileSetupUiState.Loading -> {
                    CircularProgressIndicator()
                }
                else -> {
                    Button(
                        onClick = { viewModel.completeSetup() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = userName.isNotBlank()
                    ) {
                        Text("設定を完了")
                    }
                }
            }
        }
        
        // エラーメッセージ
        errorMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { 
                        errorMessage = null 
                        viewModel.resetState()
                    }) {
                        Text("閉じる")
                    }
                }
            ) {
                Text(message)
            }
        }
    }
}
