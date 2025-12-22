package one.t10o.cheering_rocket.ui.screen.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * イベント編集画面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    eventId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit
) {
    // TODO: eventIdから既存データを取得
    var title by remember { mutableStateOf("サンプルマラソン大会") }
    var description by remember { mutableStateOf("みんなで楽しく走りましょう！") }
    var dateTime by remember { mutableStateOf("2025/01/15 09:00") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("イベント編集") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "イベント編集",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "イベントID: $eventId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("イベント名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("概要") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = dateTime,
                onValueChange = { dateTime = it },
                label = { Text("開催日時") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onSaved,
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank()
            ) {
                Text("保存")
            }
            
            OutlinedButton(
                onClick = { /* TODO: 削除確認ダイアログ */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "イベントを削除",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

