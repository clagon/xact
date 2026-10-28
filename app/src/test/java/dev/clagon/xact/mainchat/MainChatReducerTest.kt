package dev.clagon.xact.mainchat

import dev.clagon.xact.xangi.ChatMessage
import dev.clagon.xact.xangi.Role
import dev.clagon.xact.xangi.TurnEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainChatReducerTest {
    private val start = MainChatUiState(connected = true)

    @Test
    fun `正常な Turn はユーザー発話と応答の 2 件になる`() {
        val state =
            start
                .reduce(TurnEvent.Started("hi"))
                .also { assertEquals("", it.streaming) }
                .reduce(TurnEvent.Delta("he"))
                .also { assertEquals("he", it.streaming) }
                .reduce(TurnEvent.Completed("hello"))

        assertEquals(listOf(ChatMessage(Role.USER, "hi"), ChatMessage(Role.ASSISTANT, "hello")), state.messages)
        assertNull(state.streaming)
    }

    @Test
    fun `中断されたら途中までの応答を残す`() {
        val state = start.reduce(TurnEvent.Started("hi")).reduce(TurnEvent.Delta("he")).reduce(TurnEvent.Aborted)

        assertEquals(listOf(ChatMessage(Role.USER, "hi"), ChatMessage(Role.ASSISTANT, "he")), state.messages)
        assertNull(state.streaming)
    }

    @Test
    fun `応答前のエラーは発話だけ残してエラーを出す`() {
        val state = start.reduce(TurnEvent.Started("hi")).reduce(TurnEvent.Failed("boom"))

        assertEquals(listOf(ChatMessage(Role.USER, "hi")), state.messages)
        assertEquals("boom", state.error)
        assertNull(state.streaming)
    }

    @Test
    fun `次の Turn が始まると前のエラーを消す`() {
        val state = start.copy(error = "boom").reduce(TurnEvent.Started("again"))

        assertNull(state.error)
    }

    @Test
    fun `購読の開始と Turn の終了でだけ履歴を読み直す`() {
        assertTrue(TurnEvent.StreamOpened.needsHistory)
        assertTrue(TurnEvent.Completed("x").needsHistory)
        assertTrue(TurnEvent.Aborted.needsHistory)
        assertTrue(TurnEvent.Failed("x").needsHistory)
        assertFalse(TurnEvent.Started("x").needsHistory)
        assertFalse(TurnEvent.Delta("x").needsHistory)
    }
}
