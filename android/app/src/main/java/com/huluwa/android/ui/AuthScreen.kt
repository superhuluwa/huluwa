package com.huluwa.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.huluwa.android.AuthState

@Composable fun AuthScreen(auth: AuthState, login: (String, String, String, String) -> Unit, resetSetup: () -> Unit) {
    var server by rememberSaveable(auth.server) { mutableStateOf(auth.server) }
    var username by rememberSaveable(auth.username) { mutableStateOf(auth.username) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(HuluwaColors.Paper).safeDrawingPadding().imePadding()) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.Center).verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            RoundIcon(Icons.Outlined.Eco, modifier = Modifier.size(68.dp))
            Text(if (auth.setup) "第一次见面，欢迎回家" else "把家里的服务，\n装进口袋。", style = MaterialTheme.typography.headlineMedium)
            Text(if (auth.setup) "服务器尚未初始化，请创建管理员账号。" else "连接你的 huluwa 服务器，随时管理会话、下载与家庭服务。", color = HuluwaColors.Muted)
            Spacer(Modifier.height(2.dp))
            OutlinedTextField(server, { server = it; if (auth.setup) resetSetup() }, Modifier.fillMaxWidth(), label = { Text("服务器地址") },
                placeholder = { Text("http://192.168.31.88:8000") }, singleLine = true, enabled = !auth.working,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Icons.Outlined.Dns, null) })
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("账号") }, singleLine = true, enabled = !auth.working,
                shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Icons.Outlined.PersonOutline, null) })
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text(if (auth.setup) "设置密码" else "密码") }, singleLine = true,
                enabled = !auth.working, shape = RoundedCornerShape(16.dp), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), leadingIcon = { Icon(Icons.Outlined.LockOpen, null) },
                trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "隐藏密码" else "显示密码") } })
            if (auth.setup) OutlinedTextField(confirmation, { confirmation = it }, Modifier.fillMaxWidth(), label = { Text("再次输入密码") }, singleLine = true,
                enabled = !auth.working, shape = RoundedCornerShape(16.dp), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            auth.error?.let { Text(it, color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall) }
            Button({ login(server, username, password, confirmation) }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !auth.working,
                shape = RoundedCornerShape(16.dp)) {
                if (auth.working) { CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp); Spacer(Modifier.width(9.dp)) }
                Text(if (auth.working) "正在连接…" else if (auth.setup) "创建管理员并进入" else "连接并登录")
            }
            Text("任务由家庭服务器执行，手机离开应用后仍可继续下载。", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
