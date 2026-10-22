package com.huluwa.android.data

import org.junit.Assert.*
import org.junit.Test

class ServerAddressTest {
    @Test fun preservesReverseProxyPrefixAndAvoidsDuplicateApi() {
        val normalized = ServerAddress.normalize("  https://example.test/huluwa/api/  ")
        assertEquals("https://example.test/huluwa/", normalized)
        assertEquals("/huluwa/api/chat/sessions", ServerAddress.endpoint(normalized, listOf("chat", "sessions")).encodedPath)
    }
    @Test fun encodesTunnelNamesAsOnePathSegment() {
        val target = ServerAddress.endpoint("http://example.test:8000", listOf("frp", "tunnels", "家庭 NAS", "toggle"), mapOf("enabled" to "false"))
        assertEquals("家庭 NAS", target.pathSegments[3])
        assertEquals("false", target.queryParameter("enabled"))
        assertFalse(target.encodedPath.contains(" "))
    }
    @Test fun socketUsesTheSameReverseProxyPrefix() {
        val target = ServerAddress.socket("https://example.test/huluwa", "sample-token")
        assertEquals("/huluwa/ws/chat", target.encodedPath)
        assertEquals("sample-token", target.queryParameter("token"))
    }
    @Test fun aiSocketUsesTheSameReverseProxyPrefix() {
        val target = ServerAddress.aiSocket("https://example.test/huluwa/", "session-1", "sample-token")
        assertEquals("/huluwa/ws/ai/session-1", target.encodedPath)
        assertEquals("sample-token", target.queryParameter("token"))
    }
    @Test fun rejectsCredentialsAndPageLinks() {
        for (value in listOf("http://user:password@example.test", "https://example.test/#/messages", "file:///tmp/server", "https://example.test/?token=sample")) {
            assertTrue(value, runCatching { ServerAddress.normalize(value) }.isFailure)
        }
    }
}
