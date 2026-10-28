package dev.clagon.xact.xangi

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XangiClientTest {
    private val server = MockWebServer()
    private lateinit var client: XangiClient

    @Before
    fun setUp() {
        server.start()
        client = XangiClient(server.url("/").toString().trimEnd('/'), OkHttpClient())
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun json(
        body: String,
        code: Int = 200,
    ) = MockResponse
        .Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    @Test
    fun `Session を作ってからタイトルを付ける`() =
        runBlocking {
            server.enqueue(json("""{"ok":true,"sessionId":"s1"}"""))
            server.enqueue(json("""{"ok":true}"""))

            assertEquals("s1", client.createSession("[main] Main Chat"))

            val create = server.takeRequest()
            assertEquals("POST", create.method)
            assertEquals("/api/sessions", create.url.encodedPath)
            val patch = server.takeRequest()
            assertEquals("PATCH", patch.method)
            assertEquals("/api/sessions/s1", patch.url.encodedPath)
            assertEquals("""{"title":"[main] Main Chat"}""", patch.body?.utf8())
        }

    @Test
    fun `タイトルで Session を探す`() =
        runBlocking {
            server.enqueue(json("""{"sessions":[{"id":"s1","title":"[main] Main Chat","lifecycle":"open"}]}"""))

            assertEquals(listOf(SessionSummary("s1", "[main] Main Chat")), client.findSessions("[main] "))
            assertEquals("[main] ", server.takeRequest().url.queryParameter("q"))
        }

    @Test
    fun `履歴は user と assistant だけを取り出す`() =
        runBlocking {
            server.enqueue(
                json(
                    """{"id":"s1","messages":[
                    {"id":"1","role":"user","content":"こんにちは"},
                    {"id":"2","role":"assistant","content":"**やあ**"},
                    {"id":"3","role":"system","content":"x"},
                    {"id":"4","role":"assistant","content":null}
                    ]}""",
                ),
            )

            assertEquals(
                listOf(
                    ChatMessage(Role.USER, "こんにちは"),
                    ChatMessage(Role.ASSISTANT, "**やあ**"),
                    ChatMessage(Role.ASSISTANT, ""),
                ),
                client.messages("s1"),
            )
        }

    @Test
    fun `送信は device inbox に Session を指定して投げる`() =
        runBlocking {
            server.enqueue(json("""{"accepted":true}""", code = 202))

            client.send("s1", "今日の天気は？")

            val request = server.takeRequest()
            assertEquals("/api/device/inbox", request.url.encodedPath)
            assertEquals("""{"text":"今日の天気は？","appSessionId":"s1","source":"xact"}""", request.body?.utf8())
        }

    @Test
    fun `エラー応答は xangi のメッセージ付きの例外になる`() =
        runBlocking {
            server.enqueue(json("""{"error":"Session is busy"}""", code = 409))

            val e = runCatching { client.send("s1", "x") }.exceptionOrNull()

            assertTrue(e is XangiException)
            assertEquals(409, (e as XangiException).status)
            assertEquals("Session is busy", e.message)
        }

    @Test
    fun `SSE から Turn の進行を読み取る`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("Content-Type", "text/event-stream")
                    .body(
                        ": events stream\n\n" +
                            "event: ready\ndata: {\"instance_id\":\"x\"}\n\n" +
                            "data: {\"type\":\"turn.started\",\"user_text\":\"hi\"}\n\n" +
                            ": keepalive\n\n" +
                            "data: {\"type\":\"message.delta\",\"text\":\"he\",\"full_text\":\"he\"}\n\n" +
                            "data: {\"type\":\"turn.complete\",\"text\":\"hello\"}\n\n",
                    ).build(),
            )

            val events = client.events("s1").take(3).toList()

            assertEquals(
                listOf(TurnEvent.Started("hi"), TurnEvent.Delta("he"), TurnEvent.Completed("hello")),
                events,
            )
            assertEquals("web:s1", server.takeRequest().url.queryParameter("thread_id"))
        }

    @Test
    fun `Turn に関係しないイベントと壊れた JSON は無視する`() {
        assertEquals(null, parseTurnEvent("""{"type":"timeout.extended"}"""))
        assertEquals(null, parseTurnEvent("not json"))
        assertEquals(TurnEvent.Aborted, parseTurnEvent("""{"type":"turn.aborted"}"""))
        assertEquals(TurnEvent.Failed("boom"), parseTurnEvent("""{"type":"agent.error","message":"boom"}"""))
    }
}
