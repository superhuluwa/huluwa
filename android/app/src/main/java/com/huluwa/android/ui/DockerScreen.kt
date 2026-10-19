package com.huluwa.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*
import java.util.Locale

@Composable fun DockerScreen(ui: AppState, vm: HuluwaViewModel) {
    var keyword by rememberSaveable(ui.dockerSection) { mutableStateOf("") }
    val section = ui.dockerSection
    val containers = ui.containers.data.filter { "${it.name} ${it.image}".contains(keyword.trim(), true) }
    val resourceState: LoadState<List<DockerResource>> = ui.resources[section] ?: LoadState(emptyList())
    val resources = resourceState.data.filter { "${it.name} ${it.subtitle}".contains(keyword.trim(), true) }
    val enabled = ui.busy == null && !ui.containers.loading
    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel(Modifier.fillMaxWidth(), HuluwaColors.Pine) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.DirectionsBoat, null, Modifier.size(32.dp), tint = HuluwaColors.Lime)
                    Column(Modifier.weight(1f)) {
                        Text("我的 NAS", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text("Docker Engine ${ui.overview.version}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .8f))
                    }
                    Text(if (!ui.containers.loaded) "连接中" else if (ui.overview.online) "运行正常" else "连接异常", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Lime)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 19.dp)) {
                    Metric(ui.overview.total.toString(), "全部容器", Modifier.weight(1f), Color.White)
                    Metric(ui.overview.running.toString(), "运行中", Modifier.weight(1f), Color.White)
                    Metric((ui.overview.total - ui.overview.running).coerceAtLeast(0).toString(), "已停止", Modifier.weight(1f), Color.White)
                }
                HorizontalDivider(color = Color.White.copy(alpha = .2f))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("容器 CPU 合计", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f))
                        Text(ui.overview.usage.cpuPercent?.let { String.format(Locale.ROOT, "%.1f%%", it) } ?: "—", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("容器内存合计", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f))
                        Text(formatBytes(ui.overview.usage.memoryBytes), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (ui.containers.loaded && !ui.overview.online && ui.overview.message.isNotBlank()) Text(ui.overview.message, Modifier.padding(top = 12.dp), color = HuluwaColors.Amber, style = MaterialTheme.typography.bodySmall)
            FilterRow(DockerSection.entries.map { it.title }, section.ordinal, { vm.selectDockerSection(DockerSection.entries[it]) }, Modifier.padding(top = 10.dp))
            SearchBox(keyword, { keyword = it }, if (section == DockerSection.Containers) "搜索容器名称或镜像" else "搜索${section.title}")
            SectionTitle(if (section == DockerSection.Containers) "我的容器" else "${section.title}列表") {
                IconButton({ if (section == DockerSection.Containers) vm.refreshDocker() else vm.refreshResources() }, enabled = !ui.containers.loading && !resourceState.loading) { Icon(Icons.Outlined.Refresh, "刷新资源") }
                if (section != DockerSection.Containers) IconButton({ vm.showSheet(AppSheet.CreateResource(section)) }, enabled = enabled) { Icon(Icons.Outlined.Add, if (section == DockerSection.Images) "拉取镜像" else "新增${section.title}") }
            }
            Feedback(if (section == DockerSection.Containers) ui.containers.loading else resourceState.loading,
                if (section == DockerSection.Containers) ui.containers.error else resourceState.error,
                { if (section == DockerSection.Containers) vm.refreshDocker() else vm.refreshResources() })
        }
        if (section == DockerSection.Containers) {
            if (containers.isEmpty()) item { EmptyContent("没有找到容器", "容器由当前服务器的 Docker 守护进程提供", Icons.Outlined.Inventory2, !ui.containers.loaded) }
            items(containers, key = { it.id }) { container -> ContainerCard(container, enabled, vm) }
        } else {
            if (resources.isEmpty()) item { EmptyContent("没有找到${section.title}", "点击右侧加号添加资源", Icons.Outlined.Storage, !resourceState.loaded) }
            items(resources, key = { it.id }) { resource -> DockerResourceCard(section, resource, enabled && !resourceState.loading, vm) }
        }
        item { Spacer(Modifier.height(14.dp)) }
    }
}
@Composable private fun ContainerCard(container: DockerContainer, enabled: Boolean, vm: HuluwaViewModel) {
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { vm.containerDetail(container) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIcon(Icons.Outlined.Inventory2, if (container.running) HuluwaColors.Pine else HuluwaColors.Muted)
            Column(Modifier.weight(1f)) {
                Text(container.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(container.image, style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusTag(when (container.state) { "running" -> "运行中"; "paused" -> "已暂停"; "restarting" -> "重启中"; "created" -> "已创建"; "dead" -> "异常"; else -> "已停止" }, container.running, container.state == "dead")
        }
        Text(container.ports.ifBlank { container.status }, Modifier.padding(top = 11.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
        HorizontalDivider(color = HuluwaColors.Line)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton({ vm.containerLogs(container) }) { Icon(Icons.Outlined.Subject, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("日志") }
            TextButton({ vm.containerAction(container, "restart") }, enabled = enabled) { Icon(Icons.Outlined.RestartAlt, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("重启") }
            TextButton({ vm.containerAction(container, if (container.running) "stop" else "start") }, enabled = enabled) {
                Icon(if (container.running) Icons.Outlined.StopCircle else Icons.Outlined.PlayCircleOutline, null, Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp)); Text(if (container.running) "停止" else "启动", color = if (container.running) HuluwaColors.Amber else HuluwaColors.Pine)
            }
        }
    }
}
@Composable private fun DockerResourceCard(section: DockerSection, resource: DockerResource, enabled: Boolean, vm: HuluwaViewModel) {
    var menu by remember { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIcon(when (section) { DockerSection.Images -> Icons.Outlined.Layers; DockerSection.Networks -> Icons.Outlined.AccountTree; else -> Icons.Outlined.Storage })
            Column(Modifier.weight(1f)) {
                Text(resource.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(resource.subtitle.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, "资源操作") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("详细信息") }, onClick = { menu = false; vm.loadText(resource.name) { resource.raw.entries.filter { it.key !in setOf("Options", "options") }.joinToString("\n\n") { (key, value) -> "$key\n$value" } } })
                    DropdownMenuItem(text = { Text("删除${section.title}") }, enabled = enabled, onClick = { menu = false; vm.removeResource(section, resource) })
                }
            }
        }
    }
}
