package dev.clagon.xact.xangi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SessionSummary(
    val id: String,
    val title: String,
)

enum class Role { USER, ASSISTANT }

data class ChatMessage(
    val role: Role,
    val text: String,
)

/** 1 つの Turn の進行。xangi の events SSE の操作イベントに対応する。 */
sealed interface TurnEvent {
    data class Started(
        val userText: String,
    ) : TurnEvent

    data class Delta(
        val fullText: String,
    ) : TurnEvent

    data class Completed(
        val text: String,
    ) : TurnEvent

    data object Aborted : TurnEvent

    data class Failed(
        val message: String,
    ) : TurnEvent
}

class XangiException(
    val status: Int,
    message: String,
) : IOException(message)

/**
 * xangi の Web Chat API と events SSE を扱う唯一の場所（ADR 0003）。
 * 送信は `/api/device/inbox`（202 を即返す）、応答は `/api/events/stream` で受け取る。
 */
class XangiClient(
    baseUrl: String,
    private val http: OkHttpClient,
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val sse = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    /** タイトルの部分一致で Session を探す。xangi 側は大文字小文字を区別せずに絞り込む。 */
    suspend fun findSessions(query: String): List<SessionSummary> {
        val url =
            base
                .newBuilder()
                .addPathSegments("api/sessions")
                .addQueryParameter("q", query)
                .build()
        val sessions = getJson(url).jsonObject["sessions"]?.jsonArray ?: JsonArray(emptyList())
        return sessions.map {
            val obj = it.jsonObject
            SessionSummary(obj.string("id"), obj.string("title"))
        }
    }

    /** 新しい Web Session を作り、タイトルを付ける。xangi は作成時にタイトルを受け取らないため 2 回呼ぶ。 */
    suspend fun createSession(title: String): String {
        val created = call(request("api/sessions").post(EMPTY_JSON.toRequestBody(JSON_TYPE)))
        val id = json.parseToJsonElement(created).jsonObject.string("sessionId")
        val body = buildJsonObject { put("title", title) }.toString()
        call(request("api/sessions/$id").patch(body.toRequestBody(JSON_TYPE)))
        return id
    }

    // ponytail: 直近 100 件だけ取得する。古い履歴を遡るならページング（cursor）を足す
    suspend fun messages(sessionId: String): List<ChatMessage> {
        val url =
            base
                .newBuilder()
                .addPathSegments("api/sessions")
                .addPathSegment(sessionId)
                .addQueryParameter("limit", "100")
                .build()
        val messages = getJson(url).jsonObject["messages"]?.jsonArray ?: JsonArray(emptyList())
        return messages.mapNotNull { element ->
            val obj = element.jsonObject
            val role =
                when (obj.string("role")) {
                    "user" -> Role.USER
                    "assistant" -> Role.ASSISTANT
                    else -> return@mapNotNull null
                }
            ChatMessage(role, obj.string("content"))
        }
    }

    /** 送信を受理させるだけ。応答は [events] に流れる。Turn の実行中は 409 になる。 */
    suspend fun send(
        sessionId: String,
        text: String,
    ) {
        val body =
            buildJsonObject {
                put("text", text)
                put("appSessionId", sessionId)
                put("source", "xact")
            }.toString()
        call(request("api/device/inbox").post(body.toRequestBody(JSON_TYPE)))
    }

    /** 指定 Session の Turn の進行を流す。接続が切れると Flow は例外で終わる。 */
    fun events(sessionId: String): Flow<TurnEvent> =
        callbackFlow {
            val url =
                base
                    .newBuilder()
                    .addPathSegments("api/events/stream")
                    .addQueryParameter("thread_id", "web:$sessionId")
                    .build()
            val listener =
                object : EventSourceListener() {
                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String,
                    ) {
                        if (type == null) parseTurnEvent(data)?.let { trySend(it) }
                    }

                    override fun onClosed(eventSource: EventSource) {
                        close(IOException("events stream closed"))
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?,
                    ) {
                        close(t ?: IOException("events stream failed: ${response?.code}"))
                    }
                }
            val source = EventSources.createFactory(sse).newEventSource(Request.Builder().url(url).build(), listener)
            awaitClose { source.cancel() }
        }

    private fun request(path: String) = Request.Builder().url(base.newBuilder().addPathSegments(path).build())

    private suspend fun getJson(url: HttpUrl) = json.parseToJsonElement(call(Request.Builder().url(url)))

    private suspend fun call(builder: Request.Builder): String =
        withContext(Dispatchers.IO) {
            http.newCall(builder.build()).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    val message = runCatching { json.parseToJsonElement(body).jsonObject.string("error") }.getOrNull()
                    throw XangiException(response.code, message?.ifEmpty { null } ?: "HTTP ${response.code}")
                }
                body
            }
        }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val JSON_TYPE = "application/json".toMediaType()
        const val EMPTY_JSON = "{}"
    }
}

/** events SSE の `data:` 1 件を [TurnEvent] にする。Turn に関係しないイベントは null。 */
internal fun parseTurnEvent(data: String): TurnEvent? {
    val obj = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
    return when (obj.string("type")) {
        "turn.started" -> TurnEvent.Started(obj.string("user_text"))
        "message.delta" -> TurnEvent.Delta(obj.string("full_text"))
        "turn.complete" -> TurnEvent.Completed(obj.string("text"))
        "turn.aborted" -> TurnEvent.Aborted
        "agent.error" -> TurnEvent.Failed(obj.string("message"))
        else -> null
    }
}

/** xangi は応答によって値の型が揺れるため、文字列以外は JSON 文字列にする。null や欠落は空文字。 */
private fun JsonObject.string(key: String): String {
    val value = this[key]
    if (value == null || value is JsonNull) return ""
    return (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: value.toString()
}
