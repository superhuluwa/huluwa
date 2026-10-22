package com.huluwa.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.AiUiState
import com.huluwa.android.AiViewModel
import com.huluwa.android.data.*
import kotlinx.serialization.json.*

/** Ai 首页：服务端项目、供应商与模型选择，以及最近的对话。 */
@Composable fun AiScreen(ui: AiUiState, vm: AiViewModel) {
    var connection by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AiSession?>(null) }
    BackHandler(connection) { connection = false }
    LaunchedEffect(ui.authenticated, ui.connectionRevision) { if (ui.authenticated) connection = false }
    if (!ui.authenticated || connection) {
        AiConnection(ui, { server, username, password -> vm.login(server, username, password) }, if (ui.authenticated) ({ connection = false }) else null)
        return
    }
    Column(Modifier.fillMaxSize()) {
        ui.error?.let { AiError(it, vm::clearError) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "connection") {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundIcon(Icons.Outlined.Dns)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(ui.username, style = MaterialTheme.typography.titleSmall)
                            Text(ui.server, style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(vm::refresh, enabled = !ui.loading) {
                            if (ui.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Refresh, "刷新项目和模型")
                        }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, "服务连接操作") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("服务连接") }, onClick = { menu = false; connection = true; vm.clearError() })
                                DropdownMenuItem(text = { Text("退出 AI 服务") }, onClick = { menu = false; vm.logout() })
                            }
                        }
                    }
                }
            }
            item(key = "launch") {
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("开始新的对话", style = MaterialTheme.typography.titleLarge)
                        Text("选择项目和模型", style = MaterialTheme.typography.bodyMedium, color = HuluwaColors.Muted)
                        AiChoiceField("项目", ui.launchProject?.name, "选择服务端项目", ui.projects.map { AiChoice(it.path, it.name, it.path) }, !ui.loading && !ui.working, vm::selectProject)
                        AiChoiceField("模型供应商", ui.launchProvider?.label, "选择模型供应商", ui.availableProviders.map {
                            AiChoice(it.id, it.label, if (it.isDefault) "服务端默认供应商" else "${it.models.size} 个可用模型")
                        }, !ui.loading && !ui.working, vm::selectProvider)
                        AiChoiceField("模型", ui.launchModel?.title, "选择模型", ui.launchProvider?.models.orEmpty().map {
                            AiChoice(it.id, it.title, if (it.effort.isBlank()) "" else "推理等级：${it.effortLabel}")
                        }, !ui.loading && !ui.working, vm::selectModel)
                        when {
                            ui.loading -> Text("正在同步项目和模型…", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                            ui.projects.isEmpty() -> Text("服务端还没有登记项目，请先添加项目后刷新。", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                            ui.availableProviders.isEmpty() -> Text("该项目尚未启用 Claude 或 Codex，请在服务端配置。", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                            ui.launchProvider?.models?.isEmpty() == true -> Text("该供应商还没有可用模型，请在服务端配置后刷新。", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(vm::create, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = ui.canCreate, shape = RoundedCornerShape(16.dp)) {
                            if (ui.working) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                            Text(if (ui.working) "正在创建…" else "开始对话")
                        }
                    }
                }
            }
            item(key = "recent-title") { Text("最近对话", style = MaterialTheme.typography.titleMedium) }
            if (ui.sessions.isEmpty()) item(key = "empty") { Text("还没有对话，选择上方项目和模型即可开始。", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodyMedium) }
            items(ui.sessions, key = { it.id }) { session ->
                Surface(onClick = { vm.open(session.id) }, color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundIcon(Icons.Outlined.AutoAwesome)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(session.targetLabel, style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Pine, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${session.statusLabel} · ${session.projectPath}", style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton({ deleting = session }, enabled = !session.running && !ui.working) { Icon(Icons.Outlined.DeleteOutline, "删除对话", tint = HuluwaColors.Muted) }
                    }
                }
            }
        }
    }
    deleting?.let { session -> AiDeleteDialog(session, { deleting = null }) { deleting = null; vm.delete(session) } }
}

private data class AiChoice(val id: String, val title: String, val subtitle: String = "")

@Composable private fun AiChoiceField(label: String, value: String?, placeholder: String, choices: List<AiChoice>, enabled: Boolean, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = HuluwaColors.Muted)
        Box {
            OutlinedButton({ expanded = true }, Modifier.fillMaxWidth(), enabled = enabled && choices.isNotEmpty(), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(14.dp)) {
                Text(value ?: placeholder, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                Icon(Icons.Outlined.ExpandMore, null)
            }
            DropdownMenu(expanded && enabled, { expanded = false }, Modifier.heightIn(max = 360.dp).widthIn(max = 560.dp)) {
                choices.forEach { choice ->
                    DropdownMenuItem(text = {
                        Column {
                            Text(choice.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (choice.subtitle.isNotBlank()) Text(choice.subtitle, style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }, onClick = { expanded = false; select(choice.id) })
                }
            }
        }
    }
}

/** 独立二级会话页，由应用根导航承载；首页的底部标签栏不会出现在这里。 */
@Composable fun AiConversationScreen(ui: AiUiState, vm: AiViewModel) {
    val session = ui.selected ?: return
    var deleting by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = HuluwaColors.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = Color.White) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(vm::closeSession) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回 Ai 首页") }
                    Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(session.targetLabel, style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Pine, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${session.statusLabel} · ${if (ui.connected) "实时连接" else "自动同步"}", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    }
                    if (ui.loadingSession) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, "对话操作") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("返回首页选择模型") }, onClick = { menu = false; vm.closeSession() })
                            DropdownMenuItem(text = { Text("删除对话") }, enabled = !session.running && !ui.working,
                                onClick = { menu = false; deleting = true })
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ui.error?.let { AiError(it, vm::clearError) }
            AiConversation(ui, vm, Modifier.weight(1f))
        }
    }
    if (deleting) AiDeleteDialog(session, { deleting = false }) { deleting = false; vm.delete(session) }
}

@Composable private fun AiDeleteDialog(session: AiSession, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("删除对话") }, text = { Text("删除「${session.title}」在 Ai 中保存的消息？项目文件不会被删除。") },
        confirmButton = { TextButton(confirm) { Text("删除") } }, dismissButton = { TextButton(dismiss) { Text("取消") } })
}

@Composable private fun AiConnection(ui: AiUiState, login: (String, String, String) -> Unit, back: (() -> Unit)?) {
    var server by rememberSaveable(ui.server) { mutableStateOf(ui.server) }
    var username by rememberSaveable(ui.username) { mutableStateOf(ui.username) }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (back != null) TextButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Text("返回") }
        RoundIcon(Icons.Outlined.AutoAwesome, modifier = Modifier.size(60.dp))
        Text("连接 AI 服务", style = MaterialTheme.typography.headlineSmall)
        Text("填写 TerminalBuddy 服务地址和账号，手机即可与服务器上的 Claude、Codex 对话。", color = HuluwaColors.Muted)
        OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(), label = { Text("服务地址") }, placeholder = { Text("http://192.168.1.10:9600") },
            singleLine = true, enabled = !ui.authenticating, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("服务账号") }, singleLine = true, enabled = !ui.authenticating, shape = RoundedCornerShape(16.dp))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("服务密码") }, singleLine = true, enabled = !ui.authenticating,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(16.dp), trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "显示或隐藏密码") } })
        ui.error?.let { Text(it, color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall) }
        Button({ login(server, username, password) }, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !ui.authenticating) {
            if (ui.authenticating) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(if (ui.authenticating) "正在连接…" else "连接并登录")
        }
        Text("AI 服务与葫芦娃分别登录。密码不会保存，登录令牌由设备加密保存。", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun AiConversation(ui: AiUiState, vm: AiViewModel, modifier: Modifier) {
    val session = ui.selected ?: return
    val list = rememberLazyListState()
    var initialized by remember(session.id) { mutableStateOf(false) }
    val nearBottom by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } ?: true } }
    LaunchedEffect(session.id, session.messages.size, session.messages.lastOrNull()?.text?.length) {
        if (session.messages.isNotEmpty() && (!initialized || nearBottom || session.messages.last().role == "user")) {
            list.scrollToItem(session.messages.lastIndex); initialized = true
        }
    }
    Column(modifier.fillMaxWidth().imePadding()) {
        if (session.messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(if (ui.loadingSession) "正在加载消息…" else "发送第一条消息，开始对话。", color = HuluwaColors.Muted)
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(session.messages, key = { it.id }) { AiMessageBubble(it, session.providerLabel) }
            if (session.running) item(key = "ai-status") {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Text(session.statusLabel, color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        session.error?.let { Text(it, Modifier.fillMaxWidth().heightIn(max = 100.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall) }
        session.approvals.firstOrNull()?.let { AiApprovalCard(it, ui.working, vm::answer) }
        Surface(color = Color.White) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ui.draft, vm::draft, Modifier.weight(1f), placeholder = { Text("给 ${session.providerLabel} 发消息…") }, maxLines = 5,
                    enabled = !ui.loadingSession, shape = RoundedCornerShape(16.dp), textStyle = MaterialTheme.typography.bodyMedium)
                if (session.running) FilledIconButton(vm::stop, enabled = !ui.working, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Stop, "中断回复") }
                else FilledIconButton(vm::send, enabled = ui.draft.isNotBlank() && !ui.sending && !ui.loadingSession, modifier = Modifier.size(48.dp)) {
                    if (ui.sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Outlined.Send, "发送消息")
                }
            }
        }
    }
}

@Composable private fun AiMessageBubble(message: AiMessage, provider: String) {
    val mine = message.role == "user"
    val tool = message.role == "tool"
    var expanded by remember(message.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Text(if (mine) "我" else if (tool) "执行过程" else provider, Modifier.padding(horizontal = 4.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
        Surface(Modifier.widthIn(max = 600.dp), color = if (mine) HuluwaColors.SoftGreen else if (tool) Color(0xFFF0F2F1) else Color.White, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp)) {
                SelectionContainer {
                    Text(message.text, style = if (tool) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        fontFamily = if (tool) FontFamily.Monospace else FontFamily.Default,
                        maxLines = if (tool && !expanded) 4 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                }
                if (tool && (message.text.length > 180 || message.text.count { it == '\n' } >= 4)) TextButton({ expanded = !expanded }) { Text(if (expanded) "收起" else "展开执行日志") }
            }
        }
    }
}

@Composable private fun AiApprovalCard(approval: AiApproval, working: Boolean, answer: (AiApproval, String, JsonObject) -> Unit) {
    var responses by remember(approval.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val questions = approval.details.rows("questions").map { it.asObject() }
    Surface(Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(horizontal = 12.dp, vertical = 4.dp), color = HuluwaColors.SoftGreen, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (questions.isEmpty()) "AI 请求操作确认" else "AI 需要你的回复", style = MaterialTheme.typography.titleSmall)
            if (questions.isEmpty()) SelectionContainer { Text(approval.details.text("command", "reason", fallback = "是否允许本次文件修改或命令执行？"), style = MaterialTheme.typography.bodySmall) }
            questions.forEach { question ->
                val id = question.text("id")
                Text(question.text("question", "header"), style = MaterialTheme.typography.bodyMedium)
                question.rows("options").forEach { option ->
                    val label = option.asObject().text("label")
                    TextButton({ responses = responses + (id to label) }, enabled = !working) { Text(if (responses[id] == label) "✓ $label" else label) }
                }
                OutlinedTextField(responses[id].orEmpty(), { responses = responses + (id to it) }, Modifier.fillMaxWidth(), label = { Text("回复") }, maxLines = 3, enabled = !working)
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton({ answer(approval, "decline", JsonObject(emptyMap())) }, enabled = !working) { Text("拒绝") }
                TextButton({
                    val values = buildJsonObject { responses.forEach { (id, value) -> put(id, buildJsonObject { put("answers", buildJsonArray { add(value) }) }) } }
                    answer(approval, "accept", values)
                }, enabled = !working && questions.all { responses[it.text("id")]?.isNotBlank() == true }) { Text(if (questions.isEmpty()) "允许本次" else "发送回复") }
            }
        }
    }
}

@Composable private fun AiError(message: String, dismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(HuluwaColors.Danger.copy(alpha = .08f)).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f).padding(vertical = 8.dp), color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall)
        IconButton(dismiss) { Icon(Icons.Outlined.Close, "关闭提示", tint = HuluwaColors.Danger) }
    }
}
