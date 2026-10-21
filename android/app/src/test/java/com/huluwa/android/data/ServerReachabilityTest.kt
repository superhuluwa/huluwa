package com.huluwa.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 锁定「服务器连不上」的判定口径：只有网络层的连接失败才算断线提示。
 *
 * 界面层据此把这条文案收敛到标题栏的「重连中」徽标，业务错误（HTTP 4xx / 5xx）
 * 和请求超时仍走各自的错误卡片，不能被误判成断线。
 */
class ServerReachabilityTest {
    @Test fun connectionFailuresAreUnreachable() {
        assertEquals(ServerUnreachableMessage, ConnectException("连接被拒绝").userMessage())
        assertEquals(ServerUnreachableMessage, UnknownHostException("nas.local").userMessage())
        assertTrue(ConnectException("连接被拒绝").userMessage().isServerUnreachable())
    }
    @Test fun timeoutIsNotTreatedAsDisconnected() {
        val message = SocketTimeoutException("读取超时").userMessage()
        assertEquals("请求超时，请稍后重试", message)
        assertFalse(message.isServerUnreachable())
    }
    @Test fun businessErrorsKeepTheirOwnMessage() {
        assertFalse(ApiException(401, "登录已过期，请重新登录").userMessage().isServerUnreachable())
        assertFalse(ApiException(500, "容器启动失败").userMessage().isServerUnreachable())
        assertFalse(IllegalArgumentException("请输入名称").userMessage().isServerUnreachable())
        // 旧文案（例如服务端返回的说明）不能因为字面相近被当成断线。
        assertFalse("无法连接服务器".isServerUnreachable())
        assertFalse(null.isServerUnreachable())
    }
    @Test fun plainIoExceptionMatchesTheSharedMessage() {
        assertTrue(IOException("network unreachable").userMessage().isServerUnreachable())
    }
}
