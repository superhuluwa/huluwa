package com.huluwa.android.data

import org.junit.Assert.*
import org.junit.Test

class BrowserAddressTest {
    @Test fun addsHttpsWhenSchemeIsMissing() {
        assertEquals("https://example.com/", BrowserAddress.normalize(" example.com "))
        assertEquals("https://192.168.31.88:8000/", BrowserAddress.normalize("192.168.31.88:8000"))
    }
    @Test fun keepsExplicitSchemeAndPath() {
        assertEquals("http://192.168.31.88:8000/admin", BrowserAddress.normalize("http://192.168.31.88:8000/admin"))
    }
    @Test fun rejectsBlankInputAndNonHttpSchemes() {
        for (value in listOf("", "   ", "ftp://example.com", "file:///etc/passwd", "javascript:alert(1)")) {
            assertTrue(value, runCatching { BrowserAddress.normalize(value) }.isFailure)
        }
    }
}
