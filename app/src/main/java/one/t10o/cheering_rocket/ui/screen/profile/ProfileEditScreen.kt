package one.t10o.cheering_rocket.ui.screen.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

/**
 * プロフィール編集画面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val photoUrl by viewModel.photoUrl.collectAsState()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 画像ピッカー
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.uploadProfileImage(it) }
    }

    // UI状態の変化を監視
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ProfileEditUiState.Success -> {
                onSaved()
                viewModel.resetState()
            }
            is ProfileEditUiState.Error -> {
                errorMessage = state.message
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("プロフィール編集") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // プロフィール画像
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = uiState !is ProfileEditUiState.UploadingImage) {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        uiState is ProfileEditUiState.UploadingImage -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        photoUrl != null -> {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "プロフィール画像",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        else -> {
                            Text(
                                text = "👤",
                                style = MaterialTheme.typography.displayMedium
                            )
                        }
                    }
                    
                    // カメラアイコン（オーバーレイ）
                    if (uiState !is ProfileEditUiState.UploadingImage) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "画像を変更",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                
                Text(
                    text = "タップして画像を変更",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = userName,
                    onValueChange = { viewModel.updateUserName(it) },
                    label = { Text("ユーザー名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState !is ProfileEditUiState.Loading &&
                             uiState !is ProfileEditUiState.UploadingImage
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                when (uiState) {
                    is ProfileEditUiState.Loading -> {
                        CircularProgressIndicator()
                    }
                    else -> {
                        Button(
                            onClick = { viewModel.saveProfile() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = userName.isNotBlank() &&
                                     uiState !is ProfileEditUiState.UploadingImage
                        ) {
                            Text("保存")
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
}
