<template>
    <div class="management-page">
        <header class="mgmt-header">
            <button class="icon-btn" type="button" :title="'返回' + backTitle" @click="back">
                <i class="fa-solid fa-chevron-left"></i>
            </button>
            <span class="mgmt-icon" :class="kind">
                <i :class="icon"></i>
            </span>
            <div class="mgmt-title">
                <strong>{{ title }}</strong>
                <span>{{ subtitle }}</span>
            </div>
            <slot name="actions"></slot>
            <button v-if="refreshable" class="icon-btn" type="button" :title="'刷新' + title" :disabled="loading"
                @click="$emit('refresh')">
                <i v-if="loading" class="fa-solid fa-spinner fa-spin"></i>
                <i v-else class="fa-solid fa-rotate"></i>
            </button>
        </header>

        <div class="mgmt-body">
            <div v-if="busy" class="busy-bar"></div>
            <slot></slot>
        </div>
    </div>
</template>

<script>
/**
 * 管理会话整屏壳（对齐安卓 ManagementSessionScreen）：
 *  - 顶部：返回、38px 服务图标、标题 + 副标题、页面自定义动作、刷新；
 *  - 返回键回到「系统」标签的会话列表；
 *  - 底部导航在进入管理会话时收起（路由 meta.chat），与安卓一致。
 */
import { LOCAL_SESSIONS } from '@/utils/localSessions'

const KINDS = {
    frp: { icon: 'fa-solid fa-diagram-project' },
    docker: { icon: 'fa-brands fa-docker' },
    'android-build': { icon: 'fa-brands fa-android' },
    'check-logs': { icon: 'fa-solid fa-list-check' },
    library: { icon: 'fa-solid fa-hard-drive' },
    upgrade: { icon: 'fa-solid fa-arrow-up-right-dots' }
}

export default {
    name: 'ManagementShell',
    props: {
        kind: {
            type: String,
            required: true
        },
        title: {
            type: String,
            required: true
        },
        subtitle: {
            type: String,
            default: ''
        },
        loading: {
            type: Boolean,
            default: false
        },
        /** 管理操作进行中：顶部显示一条进度带 */
        busy: {
            type: Boolean,
            default: false
        },
        refreshable: {
            type: Boolean,
            default: true
        }
    },
    emits: ['refresh'],
    computed: {
        icon() {
            return (KINDS[this.kind] || KINDS.frp).icon
        },
        backTitle() {
            const matched = Object.values(LOCAL_SESSIONS).find((item) => item.kind === this.kind)
            return matched ? matched.title : '会话'
        }
    },
    methods: {
        back() {
            this.$router.push({ name: 'system' })
        }
    }
}
</script>

<style scoped>
.management-page {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 0;
    background: var(--paper);
}

.mgmt-header {
    flex: none;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: max(10px, env(safe-area-inset-top)) 12px 10px;
    background: #fff;
    border-bottom: 1px solid var(--line);
}

.icon-btn {
    flex: none;
    width: 38px;
    height: 38px;
    border: 0;
    border-radius: 12px;
    background: none;
    color: var(--muted);
    font-size: 15px;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    justify-content: center;
}

.icon-btn:hover:not(:disabled) {
    background: var(--soft-green);
    color: var(--pine);
}

.icon-btn:disabled {
    opacity: .55;
    cursor: default;
}

.mgmt-icon {
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    border-radius: 12px;
    background: var(--soft-green);
    color: var(--pine);
    font-size: 17px;
}

.mgmt-title {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 1px;
}

.mgmt-title strong {
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.mgmt-title span {
    font-size: 11px;
    color: var(--muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.mgmt-body {
    position: relative;
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    overflow-x: hidden;
    -webkit-overflow-scrolling: touch;
}

.busy-bar {
    position: sticky;
    top: 0;
    z-index: 10;
    height: 3px;
    background: linear-gradient(90deg, var(--pine) 0%, var(--lime) 100%);
    background-size: 200% 100%;
    animation: busy 1.2s linear infinite;
}

@keyframes busy {
    from { background-position: 0 0; }
    to { background-position: 200% 0; }
}
</style>
