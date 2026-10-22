package com.huluwa.android.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable data class AiMessage(val id: String, val role: String, val text: String, val createdAt: Long = 0)
@Serializable data class AiApproval(val id: String, val method: String, val details: JsonObject = JsonObject(emptyMap()))
@Serializable data class AiSession(
    val id: String, val title: String, val provider: String, val projectPath: String,
    val providerId: String? = null, val providerName: String? = null,
    val model: String? = null, val status: String = "idle", val messages: List<AiMessage> = emptyList(),
    val approvals: List<AiApproval> = emptyList(), val error: String? = null, val updatedAt: Long = 0, val revision: Long = 0,
) {
    val running get() = status == "running" || status == "approval"
    val statusLabel get() = when (status) {
        "running" -> "正在回复"; "approval" -> "等待确认"; "error" -> "执行失败"; "interrupted" -> "已中断"; else -> "可以发送消息"
    }
    val providerLabel get() = if (provider == "codex") "Codex" else "Claude"
    val modelLabel get() = model?.takeIf(String::isNotBlank) ?: "服务端默认模型"
    val targetLabel get() = listOfNotNull(providerLabel, providerName?.takeIf(String::isNotBlank), modelLabel).joinToString(" · ")
}
@Serializable data class AiAgents(val claude: Boolean = true, val codex: Boolean = true)
@Serializable data class AiProject(val name: String, val path: String, val enabledAgents: AiAgents = AiAgents())

@Serializable data class AiCatalog(val providers: List<AiProvider> = emptyList())
@Serializable data class AiProvider(
    val id: String, val agent: String, val name: String, val isDefault: Boolean = false,
    val models: List<AiModel> = emptyList(),
) {
    val agentLabel get() = if (agent == "codex") "Codex" else "Claude"
    val label get() = "$agentLabel · $name"
}
@Serializable data class AiModel(val id: String, val label: String = "", val effort: String = "") {
    val title get() = label.ifBlank { id.ifBlank { "服务端默认模型" } }
    val effortLabel get() = when (effort) {
        "minimal" -> "极低"; "low" -> "低"; "medium" -> "中"; "high" -> "高"; "xhigh" -> "很高"; "max" -> "最高"; else -> effort
    }
}
