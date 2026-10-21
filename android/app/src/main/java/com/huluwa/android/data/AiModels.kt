package com.huluwa.android.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable data class AiMessage(val id: String, val role: String, val text: String, val createdAt: Long = 0)
@Serializable data class AiApproval(val id: String, val method: String, val details: JsonObject = JsonObject(emptyMap()))
@Serializable data class AiSession(
    val id: String, val title: String, val provider: String, val projectPath: String,
    val model: String? = null, val status: String = "idle", val messages: List<AiMessage> = emptyList(),
    val approvals: List<AiApproval> = emptyList(), val error: String? = null, val updatedAt: Long = 0, val revision: Long = 0,
) {
    val running get() = status == "running" || status == "approval"
    val statusLabel get() = when (status) {
        "running" -> "正在回复"; "approval" -> "等待确认"; "error" -> "执行失败"; "interrupted" -> "已中断"; else -> "可以发送消息"
    }
    val providerLabel get() = if (provider == "codex") "Codex" else "Claude"
}
@Serializable data class AiAgents(val claude: Boolean = true, val codex: Boolean = true)
@Serializable data class AiProject(val name: String, val path: String, val enabledAgents: AiAgents = AiAgents())
