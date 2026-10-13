<template>
    <aside class="conversation-list" :class="{ mobile }">
        <div class="list-head">
            <div class="list-title">
                <h3>会话</h3>
                <span class="count">{{ filtered.length }} 个</span>
                <!-- 手机端是全屏沉浸页面：这里提供进入控制台其它模块（Docker/首页等）的唯一入口 -->
                <button v-if="showMenu" class="menu-btn" type="button" title="更多功能" @click="$emit('menu')">
                    <i class="fa-solid fa-bars"></i>
                </button>
            </div>
            <n-input v-model:value="keyword" :size="mobile ? 'medium' : 'small'" clearable
                placeholder="搜索会话或消息内容">
                <template #prefix>
                    <i class="fa-solid fa-magnifying-glass"></i>
                </template>
            </n-input>
        </div>
        <div class="list-body">
            <div v-if="!filtered.length" class="list-empty">
                {{ keyword ? '没有匹配的会话' : '暂无会话' }}
            </div>
            <div v-for="item in filtered" :key="item.id" class="conversation"
                :class="{ active: item.id === currentKey && !mobile }" @click="$emit('select', item.id)">
                <div class="conv-icon" :class="iconClass(item)">
                    <i :class="iconOf(item)"></i>
                </div>
                <div class="conv-body">
                    <div class="conv-title">{{ item.title }}</div>
                    <div class="conv-preview">{{ previewOf(item) }}</div>
                </div>
                <div class="conv-side">
                    <span class="conv-time">{{ timeOf(item) }}</span>
                    <span v-if="item.unread > 0" class="conv-unread">{{ item.unread > 99 ? '99+' : item.unread }}</span>
                </div>
                <i v-if="mobile" class="fa-solid fa-chevron-right conv-arrow"></i>
            </div>
        </div>
    </aside>
</template>

<script>
/**
 * 会话列表（对齐 quantum ConversationList 的视觉）：
 * 44px 圆图标、标题、预览、时间、未读角标（>99 显示 99+），顶部筛选框按标题/预览本地过滤。
 * 会话由服务端提供（内置电影港 / 磁力下载 / 视频解析 / 迅雷下载 + 系统通知虚拟会话），列表不做删除。
 *
 * 手机端（mobile）：列表本身就是一级「全屏页面」——标题区留出状态栏安全区、行高与触控目标放大、
 * 右侧加箭头提示可进入聊天详情，因此不再需要高亮当前会话。
 */
import { sessionPreview, formatTimeLabel } from '@/utils/chatFormat'

export default {
    name: 'ConversationList',
    props: {
        sessions: {
            type: Array,
            default: () => []
        },
        currentKey: {
            type: String,
            default: ''
        },
        /** 手机端形态：全屏一级页面（大标题 + 大行高 + 箭头） */
        mobile: {
            type: Boolean,
            default: false
        },
        /** 是否显示「更多功能」入口（全屏沉浸页面里没有主框架菜单，需要它拉出抽屉） */
        showMenu: {
            type: Boolean,
            default: false
        }
    },
    emits: ['select', 'menu'],
    data() {
        return {
            keyword: ''
        }
    },
    computed: {
        filtered() {
            const keyword = this.keyword.trim().toLowerCase()
            if (!keyword) {
                return this.sessions
            }
            return this.sessions.filter((item) => {
                const text = (item.title + ' ' + this.previewOf(item)).toLowerCase()
                return text.includes(keyword)
            })
        }
    },
    methods: {
        previewOf(item) {
            return sessionPreview(item.last) || '（暂无内容）'
        },
        /** 列表时间走相对友好的格式（当天 HH:mm / 昨天 / 同年 MM-DD），避免宽串挤压标题 */
        timeOf(item) {
            return formatTimeLabel((item.last && item.last.createdAt) || '')
        },
        iconOf(item) {
            if (item.kind === 'system') {
                return 'fa-solid fa-bell'
            }
            if (item.kind === 'movie') {
                return 'fa-solid fa-film'
            }
            if (item.kind === 'magnet') {
                return 'fa-solid fa-magnet'
            }
            if (item.kind === 'video') {
                return 'fa-solid fa-clapperboard'
            }
            if (item.kind === 'xunlei') {
                return 'fa-solid fa-bolt'
            }
            return 'fa-regular fa-comments'
        },
        iconClass(item) {
            return {
                movie: item.kind === 'movie',
                magnet: item.kind === 'magnet',
                video: item.kind === 'video',
                xunlei: item.kind === 'xunlei',
                system: item.kind === 'system'
            }
        }
    }
}
</script>

<style scoped>
.conversation-list {
    width: 292px;
    flex: none;
    display: flex;
    flex-direction: column;
    border-right: 1px solid var(--line);
    background: rgba(20, 28, 46, .45);
    min-height: 0;
}

.list-head {
    flex: none;
    padding: 14px 14px 10px;
}

.list-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
}

.list-title h3 {
    font-size: 16px;
    font-weight: 700;
    margin: 0;
    color: var(--text);
}

.count {
    margin-left: auto;
    font-size: 11.5px;
    color: var(--text-2);
}

.list-body {
    flex: 1;
    overflow-y: auto;
    padding: 2px 10px 10px;
    -webkit-overflow-scrolling: touch;
}

.list-empty {
    text-align: center;
    color: var(--text-3);
    font-size: 12.5px;
    padding: 24px 0;
}

.conversation {
    position: relative;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px;
    border-radius: 12px;
    cursor: pointer;
    border: 1px solid transparent;
    transition: background .15s ease;
}

.conversation:hover {
    background: rgba(148, 163, 184, .07);
}

.conversation.active {
    background: var(--accent-weak);
    border-color: rgba(14, 165, 233, .28);
}

.conv-icon {
    width: 44px;
    height: 44px;
    flex: none;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 17px;
    background: rgba(14, 165, 233, .15);
    color: var(--accent);
}

.conv-icon.movie {
    background: rgba(99, 102, 241, .16);
    color: #A5B4FC;
}

.conv-icon.magnet {
    background: rgba(245, 158, 11, .16);
    color: #FCD34D;
}

.conv-icon.video {
    background: rgba(6, 182, 212, .16);
    color: #67E8F9;
}

.conv-icon.xunlei {
    background: rgba(0, 169, 255, .16);
    color: #67D8FF;
}

.conv-icon.system {
    background: rgba(16, 185, 129, .14);
    color: var(--ok);
}

.conv-body {
    flex: 1;
    min-width: 0;
}

.conv-title {
    font-size: 14px;
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    color: var(--text);
}

.conv-preview {
    margin-top: 2px;
    font-size: 12px;
    color: var(--text-2);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.conv-side {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    flex: none;
}

.conv-time {
    font-size: 11px;
    color: var(--text-2);
    white-space: nowrap;
}

.conv-unread {
    min-width: 18px;
    height: 18px;
    padding: 0 6px;
    border-radius: 9px;
    background: var(--danger);
    color: #fff;
    font-size: 11px;
    font-weight: 700;
    line-height: 18px;
    text-align: center;
}

.conv-arrow {
    flex: none;
    font-size: 12px;
    color: var(--text-3);
}

.menu-btn {
    width: 36px;
    height: 36px;
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px solid var(--line);
    border-radius: 50%;
    background: rgba(148, 163, 184, .06);
    color: var(--text-2);
    font-size: 14px;
    cursor: pointer;
}

.menu-btn:hover {
    color: var(--text);
    border-color: var(--line-2);
}

/* ==========================================================================
   手机端：会话列表 = 一级全屏页面
   ========================================================================== */
.conversation-list.mobile {
    width: 100%;
    border-right: 0;
    background: var(--bg);
}

.conversation-list.mobile .list-head {
    /* 状态栏安全区由公共控制台顶栏处理，内容只保留左右安全区 */
    padding: 14px max(14px, env(safe-area-inset-right)) 10px max(14px, env(safe-area-inset-left));
    border-bottom: 1px solid var(--line);
    background: linear-gradient(180deg, rgba(20, 28, 46, .9) 0%, rgba(11, 15, 25, .75) 100%);
    backdrop-filter: blur(10px);
}

.conversation-list.mobile .list-title {
    margin-bottom: 12px;
}

.conversation-list.mobile .list-title h3 {
    font-size: 22px;
    letter-spacing: .5px;
}

.conversation-list.mobile .count {
    margin-left: auto;
}

.conversation-list.mobile .count {
    font-size: 12px;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--accent-weak);
    color: var(--accent-2);
}

.conversation-list.mobile .menu-btn {
    width: 38px;
    height: 38px;
    margin-left: 2px;
    font-size: 15px;
}

.conversation-list.mobile .list-body {
    padding: 6px max(8px, env(safe-area-inset-right)) calc(env(safe-area-inset-bottom) + 12px) max(8px, env(safe-area-inset-left));
}

.conversation-list.mobile .conversation {
    gap: 14px;
    padding: 12px 10px;
    border-radius: 14px;
}

/* 覆盖式高亮：手机端点击即离开本页，无需保留选中态 */
.conversation-list.mobile .conversation:active {
    background: rgba(148, 163, 184, .1);
}

.conversation-list.mobile .conv-icon {
    width: 52px;
    height: 52px;
    font-size: 20px;
}

.conversation-list.mobile .conv-title {
    font-size: 16px;
}

.conversation-list.mobile .conv-preview {
    margin-top: 3px;
    font-size: 13px;
}

.conversation-list.mobile .conv-time {
    font-size: 11.5px;
}

.conversation-list.mobile .conv-unread {
    min-width: 20px;
    height: 20px;
    border-radius: 10px;
    line-height: 20px;
    font-size: 11.5px;
}
</style>
