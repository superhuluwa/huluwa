package com.huluwa.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*

@Composable fun DockerScreen(ui: AppState, vm: HuluwaViewModel) {
    var keyword by rememberSaveable(ui.dockerSection) { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val section = ui.dockerSection
    val reconnecting = rememberReconnecting(vm)
    val containers = ui.containers.data.filter {
        "${it.name} ${it.image}".contains(keyword.trim(), true) && when (filter) { 1 -> it.running; 2 -> !it.running; else -> true }
    }
    val resourceState: LoadState<List<DockerResource>> = ui.resources[section] ?: LoadState(emptyList())
    val resources = resourceState.data.filter { "${it.name} ${it.subtitle}".contains(keyword.trim(), true) }
    val snapshot = ui.containers.hasSnapshot()
    val known = snapshot && ui.containers.error == null && ui.overview.online && !reconnecting
    val enabled = ui.busy == null && !ui.containers.loading && known
    val connection = when {
        reconnecting -> "连接中断"
        ui.containers.error != null -> "无法连接"
        !snapshot -> if (ui.containers.loading) "获取中" else "等待连接"
        else -> "已连接"
    }
    val usage = ui.overview.usage
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (reconnecting || ui.containers.error != null) item {
            StatusNotice(if (reconnecting) "暂时连不上服务器" else "Docker 无法连接",
                if (snapshot) "保留上次数据 · ${statusTime(ui.containers.updatedAt)}" else ui.containers.error,
                if (reconnecting) HuluwaColors.Danger else HuluwaColors.Amber) { vm.refreshDocker() }
        }
        item {
            Panel(Modifier.fillMaxWidth(), HuluwaColors.PineDark) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.DirectionsBoat, null, Modifier.size(28.dp), tint = HuluwaColors.Lime)
                    Text("Docker Engine", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Tag(connection, tone = if (known) HuluwaColors.Lime else Color(0xFFFFD7BD))
                }
                Text(listOfNotNull(ui.overview.version.takeIf { snapshot && it.isNotBlank() }?.let { "v$it" },
                    statusUpdatedAt(ui.containers.updatedAt, !known)).joinToString(" · "), Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .75f))
                Row(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                    Metric(if (snapshot) ui.overview.total.toString() else "—", "全部容器", Modifier.weight(1f), Color.White)
                    Metric(if (snapshot) ui.overview.running.toString() else "—", "运行中", Modifier.weight(1f), Color.White)
                    Metric(if (snapshot) (ui.overview.total - ui.overview.running).coerceAtLeast(0).toString() else "—", "未运行", Modifier.weight(1f), Color.White)
                }
                HorizontalDivider(color = Color.White.copy(alpha = .18f))
                Row(Modifier.fillMaxWidth().padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (usage.cpuSamples < usage.expectedSamples) "容器 CPU · 部分采样" else "运行中容器 CPU 合计",
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f))
                        Text(statusPercent(usage.cpuPercent.takeIf { snapshot }), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (usage.memorySamples < usage.expectedSamples) "容器内存 · 部分采样" else "运行中容器内存合计",
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f))
                        Text(statusBytes(usage.memoryBytes.takeIf { snapshot }), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (snapshot && usage.expectedSamples > 0 && (usage.cpuSamples < usage.expectedSamples || usage.memorySamples < usage.expectedSamples)) {
                    Text("采样覆盖：CPU ${usage.cpuSamples}/${usage.expectedSamples} · 内存 ${usage.memorySamples}/${usage.expectedSamples}",
                        Modifier.padding(top = 9.dp), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f))
                }
            }
        }
        item {
            FilterRow(DockerSection.entries.map { it.title }, section.ordinal, { vm.selectDockerSection(DockerSection.entries[it]) })
            SearchBox(keyword, { keyword = it }, if (section == DockerSection.Containers) "搜索容器名称或镜像" else "搜索${section.title}")
            if (section == DockerSection.Containers) FilterRow(listOf("全部", "运行中", "未运行"), filter, { filter = it })
            SectionTitle(if (section == DockerSection.Containers) "我的容器" else "${section.title}列表") {
                if (section == DockerSection.Containers && snapshot) Text("${containers.size} 个", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                if (section != DockerSection.Containers) {
                    IconButton({ vm.refreshResources() }, enabled = !resourceState.loading) { Icon(Icons.Outlined.Refresh, "刷新资源") }
                    IconButton({ vm.showSheet(AppSheet.CreateResource(section)) }, enabled = enabled) {
                        Icon(Icons.Outlined.Add, if (section == DockerSection.Images) "拉取镜像" else "新增${section.title}")
                    }
                }
            }
            if (section != DockerSection.Containers) Feedback(false, resourceState.error.takeUnless { it.isServerUnreachable() }, { vm.refreshResources() })
        }
        if (section == DockerSection.Containers) {
            if (!ui.containers.loaded && !snapshot) item { StatusSkeleton() }
            else if (known && containers.isEmpty()) item {
                EmptyContent(if (ui.containers.data.isEmpty()) "还没有容器" else "没有找到容器",
                    if (ui.containers.data.isEmpty()) "Docker 已连接，等待部署你的第一个服务" else "试试其他筛选或关键词", Icons.Outlined.Inventory2)
            }
            items(containers, key = { it.id }) { container -> ContainerRow(container, known, vm) }
        } else {
            if (!resourceState.loaded) item { StatusSkeleton() }
            else if (known && resourceState.error == null && resources.isEmpty()) item {
                EmptyContent("没有找到${section.title}", "点击右侧加号添加资源，或试试其他关键词", Icons.Outlined.Storage)
            }
            items(resources, key = { it.id }) { resource -> DockerResourceCard(section, resource, enabled && !resourceState.loading, vm) }
        }
        item { Text("容器用量与服务器整机用量采用不同口径；未运行包含暂停、停止等状态。",
            color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable private fun ContainerRow(container: DockerContainer, current: Boolean, vm: HuluwaViewModel) {
    Panel(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button) { vm.containerDetail(container) }, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIcon(Icons.Outlined.Inventory2, if (container.running && current) HuluwaColors.Pine else HuluwaColors.Muted,
                Modifier.size(34.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(container.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(container.image, style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(container.ports.ifBlank { "无端口映射" }, style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val label = when (container.state.lowercase()) {
                    "running" -> "运行中"; "paused" -> "已暂停"; "restarting" -> "重启中"; "created" -> "已创建"
                    "removing" -> "删除中"; "dead" -> "异常"; "exited" -> "已停止"; else -> "未知"
                }
                StatusTag(if (current) label else "上次 · $label", current && container.running, current && container.state == "dead")
                Text("查看详情 ›", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelSmall)
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
