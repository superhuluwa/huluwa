<template>
    <div class="management-page">
        <!-- 标题文字已隐藏：浏览器标签已显示页面名，页面内重复一条标题栏观感冗余。
             这里只保留返回与操作按钮，做成一条无边框的轻量工具栏。 -->
        <header class="mgmt-header">
            <button class="icon-btn" type="button" :title="'返回' + title" @click="back">
                <i class="fa-solid fa-chevron-left"></i>
            </button>
            <span class="mgmt-spacer"></span>
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
 *  - 顶部：返回、页面自定义动作、刷新；标题文字不在页面内重复（浏览器标签已显示页面名）；
 *  - 返回键回到「系统」标签的会话列表；
 *  - 底部导航在进入管理会话时收起（路由 meta.chat），与安卓一致。
 */
export default {
    name: 'ManagementShell',
    props: {
        kind: {
            type: String,
            required: true
        },
        /** 会话标题：仅用于返回键 / 刷新按钮的无障碍提示，页面内不再重复显示 */
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

/* 轻量工具栏：只有返回 / 操作 / 刷新，无标题文字，去掉分割线避免看起来像第二层标题栏 */
.mgmt-header {
    flex: none;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: max(6px, env(safe-area-inset-top)) 12px 4px;
    background: var(--paper);
}

.mgmt-spacer {
    flex: 1;
    min-width: 0;
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
