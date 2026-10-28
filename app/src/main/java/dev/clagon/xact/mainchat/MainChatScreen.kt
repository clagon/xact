package dev.clagon.xact.mainchat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import dev.clagon.xact.xangi.ChatMessage
import dev.clagon.xact.xangi.Role
import dev.clagon.xact.xangi.normalizeBaseUrl

@Composable
fun MainChatScreen(
    state: MainChatUiState,
    onSetBaseUrl: (String) -> Unit,
    onEditBaseUrl: () -> Unit,
    onCancelEditBaseUrl: () -> Unit,
    onSend: (String) -> Unit,
) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.needsSetup) {
                SetupForm(state.baseUrl, onSetBaseUrl, onCancelEditBaseUrl)
                return@Column
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEditBaseUrl) { Text("接続先") }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }
            Messages(state, Modifier.weight(1f))
            Composer(enabled = state.connected && state.streaming == null, onSend = onSend)
        }
    }
}

@Composable
private fun SetupForm(
    current: String?,
    onSetBaseUrl: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(current.orEmpty()) }
    val url = normalizeBaseUrl(input)
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("xangi の接続先", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("例: 100.64.0.1:18888") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { url?.let(onSetBaseUrl) }, enabled = url != null) { Text("接続") }
            if (current != null) TextButton(onClick = onCancel) { Text("キャンセル") }
        }
    }
}

@Composable
private fun Messages(
    state: MainChatUiState,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val itemCount = state.messages.size + if (state.streaming != null) 1 else 0
    LaunchedEffect(itemCount, state.streaming) {
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.messages) { Bubble(it) }
        state.streaming?.let { item { Bubble(ChatMessage(Role.ASSISTANT, it.ifEmpty { "…" })) } }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val isUser = message.role == Role.USER
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
            if (isUser) {
                Text(message.text, Modifier.padding(12.dp))
            } else {
                Markdown(message.text, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun Composer(
    enabled: Boolean,
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f))
        Button(
            onClick = {
                onSend(text.trim())
                text = ""
            },
            enabled = enabled && text.isNotBlank(),
            modifier = Modifier.padding(start = 8.dp),
        ) { Text("送信") }
    }
}

@Preview
@Composable
private fun MainChatScreenPreview() {
    MainChatScreen(
        MainChatUiState(
            connected = true,
            messages = listOf(ChatMessage(Role.USER, "こんにちは"), ChatMessage(Role.ASSISTANT, "**こんにちは！**")),
            streaming = "考え中",
        ),
        onSetBaseUrl = {},
        onEditBaseUrl = {},
        onCancelEditBaseUrl = {},
        onSend = {},
    )
}
