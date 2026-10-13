<template>
    <div class="header-bar">
        <button class="icon-btn" type="button" @click="$emit('on-coll-change', !collapsed)"
            :title="mobile ? '打开菜单' : (collapsed ? '展开侧栏' : '收起侧栏')">
            <i :class="mobile ? 'fa-solid fa-bars' : (collapsed ? 'fa-solid fa-indent' : 'fa-solid fa-outdent')"></i>
        </button>

        <n-breadcrumb class="crumb">
            <n-breadcrumb-item>
                <i class="fa-solid fa-house crumb-home"></i>
                <span>{{ currentTitle }}</span>
            </n-breadcrumb-item>
        </n-breadcrumb>

        <div class="custom-content-con">
            <slot></slot>
        </div>
    </div>
</template>

<script>
export default {
    name: 'HeaderBar',
    props: {
        collapsed: Boolean,
        /** 移动端：顶栏按钮改为抽屉开关（汉堡图标），与桌面端的侧栏折叠按钮区分 */
        mobile: Boolean
    },
    emits: ['on-coll-change'],
    computed: {
        currentTitle() {
            return (this.$route.meta && this.$route.meta.title) || '首页'
        }
    }
}
</script>

<style scoped>
.header-bar {
    box-sizing: border-box;
    height: 56px;
    flex: none;
    background: rgba(13, 21, 38, .7);
    border-bottom: 1px solid var(--line);
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 0 18px;
}

.icon-btn {
    flex: none;
    background: transparent;
    border: none;
    color: var(--text-2);
    cursor: pointer;
    font-size: 15px;
    padding: 6px 8px;
    border-radius: 8px;
    display: flex;
    align-items: center;
}

.icon-btn:hover {
    background: rgba(148, 163, 184, .08);
    color: var(--text);
}

.crumb {
    flex: 1;
    min-width: 0;
    white-space: nowrap;
}

.crumb-home {
    margin-right: 6px;
}

.custom-content-con {
    flex: none;
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 16px;
}
/* 移动端：顶栏宽度紧张，压缩间距并允许标题省略 */
@media (max-width: 900px) {
    .header-bar {
        height: calc(56px + env(safe-area-inset-top));
        gap: 8px;
        padding: env(safe-area-inset-top) max(12px, env(safe-area-inset-right)) 0 max(12px, env(safe-area-inset-left));
    }

    .icon-btn {
        width: 40px;
        height: 40px;
        justify-content: center;
    }

    .crumb {
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .crumb :deep(.n-breadcrumb-item__link) {
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .custom-content-con {
        gap: 10px;
    }
}
</style>
