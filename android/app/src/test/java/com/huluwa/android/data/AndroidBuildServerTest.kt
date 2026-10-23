package com.huluwa.android.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class AndroidBuildServerTest {
    @Test fun parsesAvailableNodesAndSelectedNodeReadiness() {
        val status = AndroidBuildStatus.from(Json.parseToJsonElement("""
            {"enabled":true,"workerOnline":true,"signingReady":true,
             "source":{"commit":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
             "servers":[{"id":"local","name":"默认服务器","online":false},
                        {"id":"windows","name":"Windows 电脑","online":true}]}
        """).jsonObject)
        assertEquals(2, status.servers.size)
        assertEquals("Windows 电脑", status.servers[1].name)
        assertFalse(status.servers[0].online)
        assertTrue(status.canBuild)
    }

    @Test fun oldServerWithoutNodeListStillParses() {
        val status = AndroidBuildStatus.from(Json.parseToJsonElement("""{"enabled":true}""").jsonObject)
        assertTrue(status.servers.isEmpty())
        assertFalse(status.canBuild)
        val task = AndroidBuildTask.from(Json.parseToJsonElement("""{"id":1,"type":"UPDATE"}""").jsonObject)
        assertEquals("local", task.serverId)
    }
}
