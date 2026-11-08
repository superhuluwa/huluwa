package com.huluwa.android.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.StarwarState
import com.huluwa.android.StarwarViewModel

/**
 * 全部像素风弹窗。
 *
 * <p>统一沿用原版 240×320 对话框样式：深色标题条 + 白底内容 + 底部「确定/返回」。
 * 需要键盘输入的（房间名、密码、聊天、短语）在弹窗下方叠加一个小的输入条，
 * 保证仍可用系统输入法。</p>
 */

// ------------------------------------------------------------------ 创建房间

@Composable
fun StarwarCreateRoomPixel(
    ui: StarwarState,
    vm: StarwarViewModel,
    font: StarwarFont,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf(ui.profile.nick + "的房间") }
    var mapId by remember { mutableIntStateOf(ui.planets.firstOrNull { !it.locked }?.id ?: 2) }
    var items by remember { mutableStateOf(true) }
    var costMode by remember { mutableIntStateOf(0) }
    var password by remember { mutableStateOf("") }
    // Canvas 的 DrawScope 里不能调 LocalContext.current，必须在 Composable 作用域先取
    val context = LocalContext.current

    // 内容区各行 y（相对内容区顶部），点击按行命中
    val planetRowY = 40f
    val itemRowY = 66f
    val costRowY = 82f

    StarwarPixelDialog("创建房间", onClose, okText = "确定", onOk = {
        vm.createRoom(name, mapId, items, costMode, password)
    }, height = 250f, onContentTap = { cx, cy ->
        when {
            // 星球一行九个
            cy in planetRowY..planetRowY + 20f -> {
                val index = ((cx - 8f) / 24f).toInt()
                ui.planets.getOrNull(index)?.takeIf { !it.locked }?.let { mapId = it.id }
            }
            // 道具开关
            cy in itemRowY..itemRowY + 14f -> items = !items
            // 费用三档
            cy in costRowY..costRowY + 14f -> costMode = (costMode + 1) % 3
        }
    }) { drawFont ->
        var y = 6f
        val nameShown = name.ifBlank { "（未填）" }
        pixelText(drawFont, "房间名：$nameShown".take(16), 8f, y, StarwarPx.Text)
        y += 18f
        pixelText(drawFont, "选择星球：", 8f, y, StarwarPx.Text)
        y += 17f
        // 星球一行九个，选中的用高亮底
        ui.planets.forEachIndexed { index, planet ->
            val x = 8f + index * 24f
            if (planet.id == mapId) fill(x - 2f, y - 1f, 24f, 24f, StarwarPx.Selected)
            pixelSprite(starwarBitmap(context, StarwarArt.planet(planet.icon)), x, y, 18f, 18f)
        }
        y += 26f
        val itemsShown = if (items) "开启" else "关闭"
        pixelText(drawFont, "道具：$itemsShown", 8f, y, StarwarPx.Text)
        y += 17f
        val costText = when (costMode) { 0 -> "免费"; 1 -> "K币"; else -> "水晶" }
        pixelText(drawFont, "费用：$costText", 8f, y, StarwarPx.Text)
        y += 17f
        val pwdShown = if (password.isBlank()) "无" else "已设置"
        pixelText(drawFont, "密码：$pwdShown", 8f, y, StarwarPx.Text)
        y += 17f
        pixelText(drawFont, "房间密码默认为空,如非特别需要,", 8f, y, StarwarPx.Muted)
        pixelText(drawFont, "请不要设定密码。", 8f, y + 17f, StarwarPx.Muted)
    }
    // 输入条：房间名 / 密码 用原型输入条（点「密码」行切换两栏不在原版范围内，这里保留房间名）
    StarwarInputBar(name, "房间名", onChange = { name = it.take(16) })
}

// ------------------------------------------------------------------ 房间密码

@Composable
fun StarwarPasswordPixel(
    ui: StarwarState,
    vm: StarwarViewModel,
    font: StarwarFont,
    onClose: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    StarwarPixelDialog("请输入房间密码", onClose, okText = "确定", onOk = {
        vm.enterRoomWithPassword(password)
    }, height = 120f) { drawFont ->
        val mask = "*".repeat(password.length)
        pixelText(drawFont, "密 码：$mask", 8f, 12f, StarwarPx.Text)
    }
    StarwarInputBar(password, "输入密码", onChange = { password = it.take(8) })
}

// ------------------------------------------------------------------ 游戏帮助

@Composable
fun StarwarHelpPixel(ui: StarwarState, font: StarwarFont, onClose: () -> Unit) {
    StarwarPixelDialog("游戏帮助", onClose, okText = null, height = 270f) { drawFont ->
        var y = 6f
        pixelText(drawFont, ui.help.introTitle.ifBlank { "游戏介绍：" }, 8f, y, StarwarPx.Text)
        y = pixelParagraph(drawFont, ui.help.intro, 8f, y + 17f, 200f, StarwarPx.Muted, 17f) + 4f
        pixelText(drawFont, ui.help.howToTitle.ifBlank { "玩法说明：" }, 8f, y, StarwarPx.Text)
        y = pixelParagraph(drawFont, ui.help.howTo, 8f, y + 17f, 200f, StarwarPx.Muted, 17f) + 4f
        pixelText(drawFont, ui.help.helpTitle.ifBlank { "操作说明：" }, 8f, y, StarwarPx.Text)
        y += 17f
        // 操作说明条目较长，按宽度截断避免冲出对话框
        ui.help.helpLines.take(5).forEach { line ->
            pixelText(drawFont, line.take(14), 8f, y, StarwarPx.Muted)
            y += 17f
        }
    }
}

// ------------------------------------------------------------------ 飞机商店

@Composable
fun StarwarShopPixel(ui: StarwarState, vm: StarwarViewModel, font: StarwarFont, onClose: () -> Unit) {
    val shop = ui.shop
    val context = LocalContext.current
    val items = shop?.items.orEmpty()
    var offset by remember { mutableIntStateOf(0) }
    StarwarPixelDialog(shop?.title?.ifBlank { "飞机商店" } ?: "飞机商店", onClose,
        okText = null, height = 250f, onContentTap = { _, cy ->
            // 列表从内容区 y=22 开始，每行 22
            val index = ((cy - 22f) / 22f).toInt()
            items.drop(offset).getOrNull(index)?.takeIf { !it.owned }?.let { vm.buy(it.type, it.id) }
        }) { drawFont ->
        pixelSprite(starwarBitmap(context, StarwarArt.GOLD), 8f, 4f, 32f, 11f)
        pixelText(drawFont, " ${shop?.gold ?: 0}", 42f, 3f, StarwarPx.Gold)
        pixelSprite(starwarBitmap(context, StarwarArt.CRYSTAL), 100f, 4f, 32f, 11f)
        pixelText(drawFont, " ${shop?.crystal ?: 0}", 134f, 3f, StarwarPx.Crystal)
        var y = 22f
        items.drop(offset).take(9).forEach { item ->
            val bmp = if (item.type == "plane") StarwarArt.plane(item.id) else StarwarArt.ENGINE
            pixelSprite(starwarBitmap(context, bmp), 10f, y, 18f, 18f)
            pixelText(drawFont, item.name.take(10), 34f, y + 2f, StarwarPx.Text)
            val price = if (item.gold > 0) "${item.gold}K币" else "${item.crystal}水晶"
            pixelText(drawFont, price, 200f, y + 2f,
                if (item.gold > 0) StarwarPx.Gold else StarwarPx.Crystal, align = 2)
            y += 22f
        }
        if (items.isEmpty()) {
            pixelText(drawFont, "商店暂无商品", 120f, 90f, StarwarPx.Muted, align = 1)
        }
    }
}

// ------------------------------------------------------------------ 我的机库

@Composable
fun StarwarHangarPixel(ui: StarwarState, vm: StarwarViewModel, font: StarwarFont, onClose: () -> Unit) {
    val context = LocalContext.current
    StarwarPixelDialog("我的机库", onClose, okText = null, height = 240f,
        onContentTap = { _, cy ->
            // 列表从内容区 y=8 开始，每行 24
            val index = ((cy - 8f) / 24f).toInt()
            ui.hangar.getOrNull(index)?.let { item ->
                vm.equip(item["type"].orEmpty(), item["id"].orEmpty())
            }
        }) { drawFont ->
        var y = 8f
        ui.hangar.take(9).forEach { item ->
            val isPlane = item["type"] == "plane"
            val bmp = if (isPlane) StarwarArt.plane(item["id"].orEmpty()) else StarwarArt.ENGINE
            pixelSprite(starwarBitmap(context, bmp), 10f, y, 18f, 18f)
            pixelText(drawFont, item["id"].orEmpty(), 34f, y + 2f, StarwarPx.Text)
            pixelText(drawFont, if (isPlane) "飞机" else "引擎", 90f, y + 2f, StarwarPx.Muted)
            val equipped = (isPlane && item["id"] == ui.profile.plane) ||
                    (!isPlane && item["id"] == ui.profile.engine)
            // 已装备用原版「你使用了」标签图
            if (equipped) {
                pixelSprite(starwarBitmap(context, StarwarArt.YOU_USE), 160f, y + 2f, 48f, 17f)
            } else {
                pixelText(drawFont, "装备", 210f, y + 2f, StarwarPx.Crystal, align = 2)
            }
            y += 24f
        }
        if (ui.hangar.isEmpty()) {
            pixelText(drawFont, "机库空空如也", 120f, 80f, StarwarPx.Muted, align = 1)
        }
    }
}

// ------------------------------------------------------------------ 排行榜

@Composable
fun StarwarRankingPixel(ui: StarwarState, font: StarwarFont, onClose: () -> Unit) {
    val context = LocalContext.current
    StarwarPixelDialog("游戏排行榜", onClose, okText = null, height = 250f) { drawFont ->
        pixelSprite(starwarBitmap(context, StarwarArt.RANK), 8f, 4f, 32f, 17f)
        var y = 24f
        ui.ranking.take(10).forEach { row ->
            val rankColor = when (row.ranking) {
                1 -> StarwarPx.Gold
                2 -> Color(0xFF8C9AA5)
                3 -> Color(0xFFB87333)
                else -> StarwarPx.Muted
            }
            pixelText(drawFont, "${row.ranking}", 14f, y, rankColor, align = 1)
            pixelText(drawFont, row.nick.ifBlank { row.user }.take(10), 30f, y, StarwarPx.Text)
            pixelText(drawFont, "${row.totalScore}", 228f, y, StarwarPx.Crystal, align = 2)
            pixelDivider(y + 16f, x0 = 6f, x1 = 234f, color = Color(0xFFE6ECEE))
            y += 18f
        }
        if (ui.ranking.isEmpty()) {
            pixelText(drawFont, "暂无排行数据", 120f, 90f, StarwarPx.Muted, align = 1)
        }
    }
}

// ------------------------------------------------------------------ 我的状态

@Composable
fun StarwarStatusPixel(ui: StarwarState, font: StarwarFont, onClose: () -> Unit) {
    val context = LocalContext.current
    val p = ui.profile
    StarwarPixelDialog("我的状态", onClose, okText = "确定", onOk = onClose, height = 200f) { drawFont ->
        pixelSprite(starwarBitmap(context, StarwarArt.plane(p.plane)), 12f, 10f, 20f, 20f)
        pixelText(drawFont, p.nick.ifBlank { p.user }, 38f, 10f, StarwarPx.Text)
        pixelText(drawFont, "等级:${p.level} ${p.rank}", 38f, 27f, StarwarPx.Muted)
        pixelText(drawFont, "胜率:${p.winRate.toInt()}%", 38f, 44f, StarwarPx.Muted)
        pixelDivider(62f, x0 = 8f, x1 = 208f)
        // 四项数据：金币 / 水晶 / 胜 / 负
        val cells = listOf(
            Triple(StarwarArt.GOLD, p.gold.toString(), "K币"),
            Triple(StarwarArt.CRYSTAL, p.crystal.toString(), "水晶"),
            Triple(StarwarArt.CROWN, p.wins.toString(), "胜场"),
            Triple(StarwarArt.AI, p.losses.toString(), "负场"),
        )
        cells.forEachIndexed { index, (res, value, label) ->
            val x = 12f + index * 50f
            pixelSprite(starwarBitmap(context, res), x + 5f, 68f, 20f, 16f)
            pixelText(drawFont, value, x + 15f, 87f, StarwarPx.Text, align = 1)
            pixelText(drawFont, label, x + 15f, 104f, StarwarPx.Muted, align = 1)
        }
        pixelDivider(124f, x0 = 8f, x1 = 208f)
        pixelText(drawFont, "${p.wins}胜 ${p.losses}负 共${p.matches}场", 12f, 130f, StarwarPx.Muted)
    }
}

// ------------------------------------------------------------------ 房间设置

@Composable
fun StarwarSettingsPixel(
    ui: StarwarState,
    vm: StarwarViewModel,
    font: StarwarFont,
    onClose: () -> Unit,
) {
    val room = ui.room ?: return
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    // 内容区各行 y（相对内容区顶部）：星球 24、选X项 50、费X用 68
    StarwarPixelDialog("房间设置", onClose, okText = "应用", onOk = {
        vm.updateSettings(password = password)
        if (password.isNotBlank()) password = ""
    }, height = 240f, onContentTap = { cx, cy ->
        when {
            cy in 24f..46f -> {
                val index = ((cx - 8f) / 24f).toInt()
                ui.planets.getOrNull(index)?.takeIf { !it.locked }?.let { vm.updateSettings(mapId = it.id) }
            }
            cy in 50f..66f -> vm.updateSettings(items = room.itemMode != 1)
            cy in 68f..84f -> vm.updateSettings(costMode = (room.costMode + 1) % 3)
        }
    }) { drawFont ->
        var y = 8f
        // 原版「地X图 / 选X项 / 费X用 / 密X码」：X 是 K 键上的选项字母
        pixelText(drawFont, "地1图：${room.mapName}", 8f, y, StarwarPx.Text); y += 18f
        ui.planets.take(9).forEachIndexed { index, planet ->
            val x = 8f + index * 24f
            if (planet.id == room.mapId) fill(x - 2f, y - 1f, 24f, 24f, StarwarPx.Selected)
            pixelSprite(starwarBitmap(context, StarwarArt.planet(planet.icon)), x, y, 18f, 18f)
        }
        y += 26f
        val itemShown = if (room.itemMode == 1) "开启道具" else "关闭道具"
        pixelText(drawFont, "选2项：$itemShown", 8f, y, StarwarPx.Text); y += 18f
        val costText = when (room.costMode) { 0 -> "免费"; 1 -> "K币"; else -> "水晶" }
        pixelText(drawFont, "费3用：$costText", 8f, y, StarwarPx.Text); y += 18f
        val pwdShown2 = if (password.isBlank()) "留空不改" else "已输入"
        pixelText(drawFont, "密4码：$pwdShown2", 8f, y, StarwarPx.Text); y += 18f
        pixelText(drawFont, "房间密码默认为空,如非特别需要,", 8f, y, StarwarPx.Muted)
        pixelText(drawFont, "请不要设定密码。", 8f, y + 17f, StarwarPx.Muted)
    }
    StarwarInputBar(password, "新密码", onChange = { password = it.take(8) })
}

// ------------------------------------------------------------------ 聊天窗口

@Composable
fun StarwarChatPixel(ui: StarwarState, vm: StarwarViewModel, font: StarwarFont, onClose: () -> Unit) {
    StarwarPixelDialog("聊天室", onClose, okText = "发送", onOk = { vm.sendChat() }, height = 260f) { drawFont ->
        var y = 6f
        if (ui.chat.notice.isNotBlank()) {
            y = pixelParagraph(drawFont, ui.chat.notice, 8f, y, 200f, StarwarPx.Muted, 17f) + 4f
        }
        ui.chat.messages.takeLast(11).forEach { message ->
            val text = if (message.kind == "system") message.content
                       else "${message.nick}：${message.content}"
            pixelText(drawFont, text.take(19), 8f, y,
                if (message.mine) StarwarPx.Crystal else StarwarPx.Text)
            y += 14f
        }
        if (ui.chat.nextAllowedAt > System.currentTimeMillis()) {
            pixelText(drawFont, "聊天间隔10秒,请勿刷屏!", 8f, 208f, StarwarPx.Gold)
        }
    }
    StarwarInputBar(ui.chatInput, "请输入聊天内容", onChange = vm::setChatInput)
}

// ------------------------------------------------------------------ 短语

@Composable
fun StarwarPhrasePixel(ui: StarwarState, vm: StarwarViewModel, font: StarwarFont, onClose: () -> Unit) {
    val phrases = ui.chat.phrases.ifEmpty { ui.texts.defaultPhrases }
    val rows = phrases.take(6)
    StarwarPixelDialog("短语", onClose, okText = null, height = 200f,
        onContentTap = { _, cy ->
            // 点第几行就发第几条短语（原版短语列表同样是点选即发送）
            val index = ((cy - 8f) / 22f).toInt()
            rows.getOrNull(index)?.let { vm.sendChat(it, kind = "phrase") }
        }) { drawFont ->
        rows.forEachIndexed { index, phrase ->
            val y = 8f + index * 22f
            fill(6f, y, 208f, 20f, Color(0xFFF5F8F9))
            pixelBorder(6f, y, 208f, 20f, Color(0xFFDCE3E6))
            pixelText(drawFont, phrase.take(15), 12f, y + 4f, StarwarPx.Text)
        }
        if (rows.isEmpty()) {
            pixelText(drawFont, "还没有设置短语", 120f, 60f, StarwarPx.Muted, align = 1)
        }
    }
}

// ------------------------------------------------------------------ 短语设置

@Composable
fun StarwarPhraseEditPixel(
    ui: StarwarState,
    vm: StarwarViewModel,
    font: StarwarFont,
    onClose: () -> Unit,
) {
    var editing by remember { mutableIntStateOf(0) }
    val draft = ui.phraseDraft
    StarwarPixelDialog("短语设置", onClose, okText = "确定", onOk = { vm.savePhrases() }, height = 220f) { drawFont ->
        draft.take(6).forEachIndexed { index, phrase ->
            val y = 8f + index * 22f
            if (index == editing) fill(4f, y - 1f, 212f, 20f, StarwarPx.Selected)
            pixelText(drawFont, "${index + 1}.", 8f, y + 2f, StarwarPx.Muted)
            pixelText(drawFont, phrase.ifBlank { "（空）" }.take(15), 26f, y + 2f, StarwarPx.Text)
        }
    }
    StarwarInputBar(draft.getOrElse(editing) { "" }, "短语 ${editing + 1}",
        onChange = { vm.setPhraseDraft(editing, it) })
}

// ------------------------------------------------------------------ 系统菜单

@Composable
fun StarwarSystemMenuPixel(
    ui: StarwarState,
    vm: StarwarViewModel,
    font: StarwarFont,
    onCloseApp: () -> Unit,
    onClose: () -> Unit,
) {
    val entries = listOf<Pair<String, () -> Unit>>(
        "游戏帮助" to { vm.closeSystemMenu(); vm.showHelp() },
        "我的状态" to { vm.closeSystemMenu(); vm.openStatus() },
        "短语设置" to { vm.closeSystemMenu(); vm.openPhraseEdit() },
    )
    StarwarPixelDialog("菜单", onClose, okText = null, height = 220f, onContentTap = { _, cy ->
        // 三个菜单项从 y=8 起，每项 26
        val index = ((cy - 8f) / 26f).toInt()
        entries.getOrNull(index)?.second?.invoke()
    }) { drawFont ->
        var y = 8f
        entries.forEach { (label, action) ->
            fill(6f, y, 208f, 22f, Color(0xFFF5F8F9))
            pixelText(drawFont, label, 14f, y + 5f, StarwarPx.Text)
            y += 26f
        }
        // 音量：原版原文「音量调节：超级大玩家主界面->系统设置」，这里给个简易滑条
        pixelText(drawFont, "音量调节", 8f, y + 2f, StarwarPx.Text)
        fill(70f, y + 6f, 100f, 6f, Color(0xFFDCE3E6))
        fill(70f, y + 6f, 100f * ui.volume, 6f, StarwarPx.HeaderTop)
        pixelText(drawFont, "${(ui.volume * 100).toInt()}%", 178f, y + 2f, StarwarPx.Muted)
        y += 22f
        if (ui.stage == com.huluwa.android.StarwarStage.Room) {
            pixelText(drawFont, "退出房间", 14f, y + 4f, StarwarPx.Danger)
        }
    }
}

// ------------------------------------------------------------------ 对手资料

@Composable
fun StarwarOpponentPixel(ui: StarwarState, vm: StarwarViewModel, font: StarwarFont, onClose: () -> Unit) {
    val data = ui.opponent
    val context = LocalContext.current
    StarwarPixelDialog("对手资料", onClose, okText = "加为好友",
        onOk = { vm.addFriend(data?.get("user").orEmpty()) }, height = 170f) { drawFont ->
        val nick = data?.get("nick").orEmpty().ifBlank { data?.get("user").orEmpty() }
        pixelSprite(starwarBitmap(context, StarwarArt.plane(data?.get("plane").orEmpty())),
            12f, 10f, 20f, 20f)
        pixelText(drawFont, nick, 38f, 10f, StarwarPx.Text)
        val opLevel = data?.get("level").orEmpty()
        val opRank = data?.get("rank").orEmpty()
        pixelText(drawFont, "等级:$opLevel · $opRank", 38f, 24f, StarwarPx.Muted)
        val opRate = data?.get("winRate").orEmpty()
        pixelText(drawFont, "胜率:$opRate%", 38f, 38f, StarwarPx.Muted)
        val opWins = data?.get("wins").orEmpty()
        val opLosses = data?.get("losses").orEmpty()
        pixelText(drawFont, "${opWins}胜 ${opLosses}负", 38f, 52f, StarwarPx.Muted)
        val opPlane = data?.get("plane").orEmpty()
        pixelText(drawFont, "飞机:$opPlane", 12f, 74f, StarwarPx.Muted)
    }
}

// ------------------------------------------------------------------ 通用输入条

/**
 * 弹窗下方的输入条（系统输入法）。
 *
 * <p>原版是 T9 键盘输入，触摸屏上沿用一条固定的文本输入行，贴在屏幕底部、
 * 浮在 240×320 画布之上，不遮挡弹窗内容。</p>
 */
@Composable
fun StarwarInputBar(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            color = Color(0xFFF2F5F6),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = StarwarPx.Text),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) {
                            Text(placeholder, fontSize = 14.sp, color = Color(0xFF8C9AA5))
                        }
                        inner()
                    }
                },
            )
        }
    }
}
