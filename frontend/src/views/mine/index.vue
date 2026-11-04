<template>
    <div class="mine-page">
        <!-- 账号卡：与安卓 AccountCard 一致（头像 + 账号 + 服务器 + 连接状态） -->
        <section class="account-card">
            <span class="avatar">葫</span>
            <div class="account-info">
                <strong>{{ auth.username || '未登录' }}</strong>
                <span>{{ auth.server || '尚未连接服务器' }}</span>
            </div>
            <span class="status-tag" :class="{ online: chatStore.wsReady }">
                {{ chatStore.wsReady ? '已连接' : '轮询同步' }}
            </span>
        </section>

        <!-- 连接与账户 -->
        <h2 class="section-title">连接与账户</h2>
        <section class="panel">
            <button class="entry" type="button" @click="accountOpen = true">
                <i class="fa-solid fa-server entry-icon"></i>
                <span class="entry-body">
                    <strong>连接配置</strong>
                    <span>{{ auth.server || '—' }} · {{ auth.username || '—' }}</span>
                </span>
                <i class="fa-solid fa-chevron-right entry-arrow"></i>
            </button>
            <div class="divider"></div>
            <button class="entry danger" type="button" @click="logout">
                <i class="fa-solid fa-right-from-bracket entry-icon"></i>
                <span class="entry-body">
                    <strong>退出登录 / 切换服务器</strong>
                    <span>退出后服务器任务继续运行</span>
                </span>
                <i class="fa-solid fa-chevron-right entry-arrow"></i>
            </button>
        </section>

        <!-- 网页端补充的服务器工具（安卓端没有对应页面，按「系统会话」的思路保留在原处） -->
        <h2 class="section-title">服务器工具</h2>
        <section class="panel">
            <button v-for="(tool, index) in tools" :key="tool.name" class="entry" type="button" @click="open(tool)">
                <i :class="[tool.icon, 'entry-icon']"></i>
                <span class="entry-body">
                    <strong>{{ tool.title }}</strong>
                    <span>{{ tool.desc }}</span>
                </span>
                <i class="fa-solid fa-chevron-right entry-arrow"></i>
                <span v-if="index !== tools.length - 1" class="entry-divider"></span>
            </button>
        </section>

        <p class="foot">葫芦娃 · 家里的服务，随身就好。</p>

        <AccountSheet v-model:show="accountOpen" />
    </div>
</template>

<script>
/**
 * 我的标签页（对齐安卓 MineScreen）：
 *  - 顶部账号卡：当前账号、服务器地址、实时连接状态；
 *  - 「连接配置」打开账号弹层（修改密码 / 退出）；
 *  - 网页端额外保留「服务器概览」「推送设置」两个工具入口（安卓端没有对应页面）。
 */
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useFullscreenPage } from '@/composables/useFullscreenPage'
import AccountSheet from '@/components/main/components/account-sheet.vue'

export default {
    name: 'MinePage',
    components: { AccountSheet },
    setup() {
        return { auth: useAuthStore(), chatStore: useChatStore() }
    },
    data() {
        return {
            accountOpen: false,
            tools: [
                { name: 'dashboard', title: '服务器概览', desc: 'CPU、内存与运行环境', icon: 'fa-solid fa-gauge-high' },
                { name: 'push', title: '推送设置', desc: '推送渠道与订阅事件', icon: 'fa-solid fa-paper-plane' }
            ]
        }
    },
    mounted() {
        this.chatStore.refreshSessions().catch(() => {})
    },
    methods: {
        open(tool) {
            this.$router.push({ name: tool.name })
        },
        logout() {
            this.$dialog.warning({
                title: '退出登录',
                content: '退出后可以切换服务器或账号，服务器任务会继续运行。',
                positiveText: '退出',
                negativeText: '取消',
                onPositiveClick: () => {
                    this.auth.clear()
                    useFullscreenPage().value = false
                    this.$router.push({ name: 'login' })
                }
            })
        }
    }
}
</script>

<style scoped>
.mine-page {
    min-height: 100%;
    padding: 4px 22px 28px;
    background: var(--paper);
}

.account-card {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 16px;
    border-radius: 20px;
    background: var(--soft-green);
}

.avatar {
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 52px;
    height: 52px;
    border-radius: 50%;
    background: #E8F0E1;
    color: #49633B;
    font-family: Georgia, "Songti SC", serif;
    font-size: 24px;
}

.account-info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 5px;
}

.account-info strong {
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.account-info span {
    font-size: 12px;
    color: var(--muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.status-tag {
    flex: none;
    padding: 4px 8px;
    border-radius: 7px;
    background: rgba(149, 98, 35, .10);
    color: var(--amber);
    font-size: 10px;
    font-weight: 500;
}

.status-tag.online {
    background: rgba(32, 108, 84, .10);
    color: var(--pine);
}

.section-title {
    margin: 20px 0 10px;
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
}

.panel {
    padding: 4px;
    border-radius: 20px;
    background: #fff;
    border: 1px solid var(--line);
}

.entry {
    position: relative;
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    padding: 13px 10px;
    border: 0;
    border-radius: 16px;
    background: none;
    text-align: left;
    font-family: inherit;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;
}

.entry:active {
    background: var(--soft-green);
}

.entry-icon {
    flex: none;
    width: 21px;
    color: var(--pine);
    font-size: 17px;
    text-align: center;
}

.entry-body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
}

.entry-body strong {
    font-size: 14px;
    font-weight: 500;
    color: var(--ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.entry-body span {
    font-size: 11px;
    color: var(--muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.entry-arrow {
    flex: none;
    color: var(--muted);
    font-size: 12px;
}

.entry.danger .entry-icon,
.entry.danger .entry-body strong {
    color: var(--danger);
}

.divider {
    height: 1px;
    margin: 0 10px 0 43px;
    background: var(--line);
}

.entry-divider {
    position: absolute;
    left: 43px;
    right: 10px;
    bottom: 0;
    height: 1px;
    background: var(--line);
}

.foot {
    margin: 24px 0 0;
    text-align: center;
    font-size: 11px;
    color: var(--muted);
}
</style>
