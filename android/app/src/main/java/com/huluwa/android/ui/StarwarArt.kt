package com.huluwa.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import coil3.compose.rememberAsyncImagePainter
import com.huluwa.android.R

/**
 * 星球大战原始美术资源。
 *
 * <p>全部图片逐像素解自 2009 年冒泡社区 {@code starwar.mrp} 内的 54 张 16bpp RGB565 位图，
 * 按原尺寸导出为 PNG 放在 {@code drawable-nodpi/starwar_*.png}。
 * 原始文件名的语义见 [StarwarArt] 的常量。</p>
 */
object StarwarArt {
    /** AI 机器人头像 */
    const val AI = R.drawable.starwar_ai
    /** 冠军王冠 */
    const val CROWN = R.drawable.starwar_crown
    /** 玩家形象（20×20 卡通小人，原版 plane.bmp 实际画的不是飞机） */
    const val PLANE = R.drawable.starwar_plane
    /** 太阳 / 恒星 */
    const val SUN = R.drawable.starwar_sun
    /** 三种子弹 */
    const val BULLET = R.drawable.starwar_bullet
    const val BULLET1 = R.drawable.starwar_bullet1
    const val BULLET2 = R.drawable.starwar_bullet2
    /** 三种爆炸 */
    const val EXPLODE1 = R.drawable.starwar_exploid1
    const val EXPLODE2 = R.drawable.starwar_exploid2
    const val EXPLODE3 = R.drawable.starwar_exploid3
    /** 三种受损 */
    const val DAMAGE1 = R.drawable.starwar_damage1
    const val DAMAGE2 = R.drawable.starwar_damage2
    const val DAMAGE3 = R.drawable.starwar_damage3
    /** 陨石障碍 */
    const val STONE1 = R.drawable.starwar_stone1
    const val STONE2 = R.drawable.starwar_stone2
    /**
     * 文字标签图：live「存活者:」gold「金钱:」crystal「水晶:」rank「排名:」
     * scores「本方积分」operate「按OK或*键使用道具」
     */
    const val GOLD = R.drawable.starwar_gold
    const val LIVE = R.drawable.starwar_live
    const val CRYSTAL = R.drawable.starwar_crystal
    /** 血条 / 时间条 */
    const val HEALTH = R.drawable.starwar_health
    const val TIMEBAR = R.drawable.starwar_timebar
    /** 抽奖券 */
    const val LOTTERY = R.drawable.starwar_lottery
    /** 未解锁 */
    const val LOCK = R.drawable.starwar_lock
    /** 道具与道具栏 */
    const val PROP = R.drawable.starwar_prop
    const val PSLOT = R.drawable.starwar_pslot
    /** 排行榜与得分 */
    const val RANK = R.drawable.starwar_rank
    const val SCORES = R.drawable.starwar_scores
    /**
     * 以下四张是**文字标签图**而非图标，原版用它们当状态提示：
     * selfs「本方分数:」oppos「对方分数:」team「队友使用了」
     * oppoself「敌人使用了」oppouse「敌人对你使用了」youuse「你使用了」
     */
    const val TEAM = R.drawable.starwar_team
    const val YOU_USE = R.drawable.starwar_youuse
    const val OPPO_SELF = R.drawable.starwar_oppoself
    const val OPPO_USE = R.drawable.starwar_oppouse
    /** 弹壳 / 引擎 */
    const val SHELL = R.drawable.starwar_shell
    const val ENGINE = R.drawable.starwar_engine
    /** 发射特效 / 环绕 / 风暴 / 闪光 */
    const val LAUNCH = R.drawable.starwar_lauch
    const val SURROUND = R.drawable.starwar_surround
    const val FLASH = R.drawable.starwar_flash
    /** 进度条三态 */
    const val PRGS1 = R.drawable.starwar_prgs1
    const val PRGS2 = R.drawable.starwar_prgs2
    const val PRGS3 = R.drawable.starwar_prgs3
    /** 数字贴图 / 操作说明 / 列表图标 */
    const val NUMBER = R.drawable.starwar_number
    const val OPERATE = R.drawable.starwar_operate
    const val LIST_ICON = R.drawable.starwar_listicon
    /** 己方 / 对方标记（原版 8 位索引色，32×43） */
    const val SELFS = R.drawable.starwar_selfs
    const val OPPOS = R.drawable.starwar_oppos

    /** 九大星球贴图（maps.inf 的 icon 字段直接对应） */
    fun planet(icon: String): Int = when (icon) {
        "dixing" -> R.drawable.starwar_dixing
        "jinxing" -> R.drawable.starwar_jinxing
        "muxing" -> R.drawable.starwar_muxing
        "shuixing" -> R.drawable.starwar_shuixing
        "huoxing" -> R.drawable.starwar_huoxing
        "tuxing" -> R.drawable.starwar_tuxing
        "tianxing" -> R.drawable.starwar_tianxing
        "haixing" -> R.drawable.starwar_haixing
        "mingxing" -> R.drawable.starwar_mingxing
        else -> R.drawable.starwar_dixing
    }

    /** 飞机 id → 贴图（原版飞机资源名即贴图名） */
    fun plane(planeId: String): Int = when (planeId) {
        "oppos" -> R.drawable.starwar_oppoself
        "selfs" -> R.drawable.starwar_selfs
        "team" -> R.drawable.starwar_team
        "sun" -> R.drawable.starwar_sun
        else -> R.drawable.starwar_plane
    }

    /** 道具类型 → 贴图 */
    fun prop(kind: String): Int = when (kind) {
        "heal" -> R.drawable.starwar_live
        "shield" -> R.drawable.starwar_crystal
        "slow" -> R.drawable.starwar_timebar
        else -> R.drawable.starwar_lottery
    }
}

/** 直接渲染一张原始位图（保持像素风，不做平滑缩放） */
@Composable
fun StarwarImage(resId: Int, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = rememberAsyncImagePainter(
            ImageRequest.Builder(LocalContext.current).data(resId).build()
        ),
        contentDescription = null,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

/**
 * 渲染一张**文字标签图**（selfs/oppos/live/rank/gold 这类）。
 *
 * <p>这些资源是原版把中文标语直接烤进位图的结果，长宽比很扁（如 53×13、104×13）。
 * 用固定 size 会严重变形，所以这里只约束高度、宽度按原始比例自适应。</p>
 */
@Composable
fun StarwarLabel(resId: Int, height: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = rememberAsyncImagePainter(
            ImageRequest.Builder(LocalContext.current).data(resId).build()
        ),
        contentDescription = null,
        modifier = modifier.height(height).wrapContentWidth(),
        contentScale = ContentScale.Fit,
    )
}
