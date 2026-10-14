<template>
    <!-- 有官方品牌图标用品牌图标；否则退回兜底文字（如机器人 Q）或 Font Awesome 图标 -->
    <img v-if="src" class="session-icon" :src="src" :alt="alt || ''" draggable="false" />
    <span v-else-if="fallbackText" class="session-text">{{ fallbackText }}</span>
    <i v-else :class="fallbackClass"></i>
</template>

<script>
/**
 * 会话品牌图标：把内置会话（电影港 / 电影天堂 / qBittorrent / 视频解析 / 迅雷下载）
 * 的默认 Font Awesome 图标换成各服务官方图标，系统通知等无品牌的会话仍用图标字体。
 *
 * 图标文件取自官方来源（站点 favicon / 官方 LOGO / 官方安装包），见 assets/brand-icons/README.md；
 * 组件只负责渲染，尺寸与圆角由父级容器（会话列表 / 消息气泡头像）决定。
 */
import movieIcon from '@/assets/brand-icons/movie-dygangs.png'
import dyttIcon from '@/assets/brand-icons/dytt-dyttzyw.png'
import magnetIcon from '@/assets/brand-icons/magnet-qbittorrent.png'
import videoIcon from '@/assets/brand-icons/video-douyin.png'
import xunleiIcon from '@/assets/brand-icons/xunlei.png'

/** 会话类型 → 品牌图标（键与 chat_session.kind 一致） */
const BRAND_ICONS = {
    movie: movieIcon,
    dytt: dyttIcon,
    magnet: magnetIcon,
    video: videoIcon,
    xunlei: xunleiIcon
}

export default {
    name: 'SessionIcon',
    props: {
        /** 会话类型：movie / dytt / magnet / video / xunlei / system / chat */
        kind: {
            type: String,
            default: ''
        },
        /** 无品牌图标时的 Font Awesome 类名 */
        fallbackClass: {
            type: String,
            default: 'fa-regular fa-comments'
        },
        /** 无品牌图标时的兜底文字（优先于 fallbackClass，例如机器人头像的 Q） */
        fallbackText: {
            type: String,
            default: ''
        },
        /** img 的 alt 文本（默认空，图标纯装饰） */
        alt: {
            type: String,
            default: ''
        }
    },
    computed: {
        src() {
            return BRAND_ICONS[this.kind] || ''
        }
    }
}
</script>

<style scoped>
.session-text {
    line-height: 1;
}

.session-icon {
    /* 铺满父容器：品牌图标本身就是带留白的应用图标，父容器负责圆角与裁剪 */
    width: 100%;
    height: 100%;
    object-fit: contain;
    display: block;
    user-select: none;
    -webkit-user-drag: none;
}
</style>
