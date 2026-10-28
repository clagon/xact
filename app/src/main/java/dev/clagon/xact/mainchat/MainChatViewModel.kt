package dev.clagon.xact.mainchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.clagon.xact.Settings
import dev.clagon.xact.xangi.ChatMessage
import dev.clagon.xact.xangi.Role
import dev.clagon.xact.xangi.TurnEvent
import dev.clagon.xact.xangi.XangiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Main Chat の Session タイトルの接頭辞（ADR 0002 の `[side] ` と対になる）。 */
const val MAIN_CHAT_TITLE_PREFIX = "[main] "

data class MainChatUiState(
    /** 接続先が未設定なら true。設定画面を出す。 */
    val needsSetup: Boolean = false,
    val connected: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    /** 実行中の Turn の応答。null なら Turn は動いていない。空文字は応答待ち。 */
    val streaming: String? = null,
    val error: String? = null,
)

/** Turn の進行を画面状態に反映する。 */
fun MainChatUiState.reduce(event: TurnEvent): MainChatUiState =
    when (event) {
        is TurnEvent.Started -> copy(messages = messages + ChatMessage(Role.USER, event.userText), streaming = "", error = null)
        is TurnEvent.Delta -> copy(streaming = event.fullText)
        is TurnEvent.Completed -> copy(messages = messages + ChatMessage(Role.ASSISTANT, event.text), streaming = null)
        TurnEvent.Aborted -> copy(messages = messages + listOfNotNull(partialReply()), streaming = null)
        is TurnEvent.Failed -> copy(messages = messages + listOfNotNull(partialReply()), streaming = null, error = event.message)
    }

private fun MainChatUiState.partialReply() = streaming?.takeIf { it.isNotEmpty() }?.let { ChatMessage(Role.ASSISTANT, it) }

class MainChatViewModel(
    private val settings: Settings,
    private val http: OkHttpClient,
) : ViewModel() {
    private val _state = MutableStateFlow(MainChatUiState())
    val state: StateFlow<MainChatUiState> = _state.asStateFlow()

    private var client: XangiClient? = null
    private var sessionId: String? = null

    init {
        viewModelScope.launch {
            settings.baseUrl.collectLatest { url ->
                if (url == null) {
                    _state.value = MainChatUiState(needsSetup = true)
                } else {
                    _state.value = MainChatUiState()
                    connect(XangiClient(url, http))
                }
            }
        }
    }

    fun setBaseUrl(url: String) {
        viewModelScope.launch { settings.setBaseUrl(url) }
    }

    fun send(text: String) {
        val client = client ?: return
        val sessionId = sessionId ?: return
        viewModelScope.launch {
            runCatching { client.send(sessionId, text) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    /** 履歴を読み直してから SSE を購読する。切れたら少し待って最初からやり直す。 */
    private suspend fun connect(client: XangiClient) {
        this.client = client
        while (true) {
            try {
                val id = sessionId ?: resolveMainSession(client).also { sessionId = it }
                val history = client.messages(id)
                _state.update { it.copy(connected = true, messages = history, streaming = null, error = null) }
                client.events(id).collect { event -> _state.update { it.reduce(event) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(connected = false, error = "接続できません: ${e.message}") }
            }
            delay(RETRY_DELAY_MS)
        }
    }

    // ponytail: 保存済みの ID は存在確認をせずに使う。xangi 側で消えた場合は接続先の再設定で作り直す
    private suspend fun resolveMainSession(client: XangiClient): String {
        settings.mainSessionId()?.let { return it }
        val id =
            client.findSessions(MAIN_CHAT_TITLE_PREFIX).firstOrNull { it.title.startsWith(MAIN_CHAT_TITLE_PREFIX) }?.id
                ?: client.createSession("${MAIN_CHAT_TITLE_PREFIX}Main Chat")
        settings.setMainSessionId(id)
        return id
    }

    private companion object {
        const val RETRY_DELAY_MS = 3_000L
    }
}
