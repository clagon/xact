package dev.clagon.xact

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.clagon.xact.mainchat.MainChatScreen
import dev.clagon.xact.mainchat.MainChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as XactApplication).container
        setContent {
            MaterialTheme {
                val vm = viewModel { MainChatViewModel(container.settings, container.http) }
                val state by vm.state.collectAsStateWithLifecycle()
                MainChatScreen(
                    state,
                    onSetBaseUrl = vm::setBaseUrl,
                    onEditBaseUrl = vm::editBaseUrl,
                    onCancelEditBaseUrl = vm::cancelEditBaseUrl,
                    onSend = vm::send,
                )
            }
        }
    }
}
