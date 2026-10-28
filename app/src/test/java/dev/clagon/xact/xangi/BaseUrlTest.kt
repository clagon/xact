package dev.clagon.xact.xangi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaseUrlTest {
    @Test
    fun `scheme を省いた入力に http を補う`() {
        assertEquals("http://100.64.0.1:18888", normalizeBaseUrl("100.64.0.1:18888"))
    }

    @Test
    fun `前後の空白と末尾のスラッシュを除く`() {
        assertEquals("https://xangi.tail.ts.net", normalizeBaseUrl("  https://xangi.tail.ts.net/ "))
    }

    @Test
    fun `解釈できない入力は null`() {
        assertNull(normalizeBaseUrl(""))
        assertNull(normalizeBaseUrl("ftp://host"))
        assertNull(normalizeBaseUrl("http://host:18888/api"))
        assertNull(normalizeBaseUrl("http://"))
        assertNull(normalizeBaseUrl("127.0.0.1:99999"))
    }
}
